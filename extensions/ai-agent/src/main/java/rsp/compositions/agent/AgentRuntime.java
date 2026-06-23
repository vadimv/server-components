package rsp.compositions.agent;

import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.agent.ActionDispatcher.DispatchResult;
import rsp.compositions.agent.AgentService.AgentResult;
import rsp.compositions.authorization.AccessDecision;
import rsp.compositions.authorization.AttributeKeys;
import rsp.compositions.authorization.Attributes;
import rsp.compositions.authorization.Authorization;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ActionBindings;
import rsp.compositions.contract.ContractAction;
import rsp.compositions.contract.ContractActionPayload;
import rsp.compositions.contract.DispatchEffect;
import rsp.compositions.contract.EditViewContract;
import rsp.compositions.contract.EventKeys;
import rsp.compositions.contract.FormViewContract;
import rsp.compositions.contract.ListViewContract;
import rsp.compositions.contract.Scene;
import rsp.compositions.contract.ViewContract;
import rsp.util.html.HtmlEscape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates LLM calls, authorization, action dispatch and plan execution
 * on behalf of a host contract (e.g. {@code PromptContract}).
 * <p>
 * The runtime is presentation-agnostic: status updates flow through
 * {@link AgentFeedback}. The host contract supplies the active {@link Scene}
 * via {@link #onScene(Scene)} and routes user input via {@link #submit(String)}.
 * <p>
 * <b>Multi-step loop (Phase 1B):</b> a {@code submit} iterates — for each
 * dispatched {@code ActionResult} or {@code NavigateResult}, the runtime
 * re-materialises {@link AgentContext} from the now-current scene and invokes
 * the LLM again with the original prompt. The loop terminates on
 * {@code TextReply}, {@code PlanResult}, dispatch failure, awaiting-confirm,
 * approval-required, cancellation, or when {@link LoopPolicy#shouldContinue}
 * returns {@code false}.
 * <p>
 * <b>Cancellation:</b> {@link #cancel()} flips the in-flight token; the
 * loop checks it at iteration boundaries. The {@link AbortToken} is also
 * passed to {@link AgentService#handlePrompt} so LLM clients can abort
 * the underlying HTTP call.
 * <p>
 * <b>Concurrency:</b> {@code submit} rejects re-entry while the loop is
 * running. State touched from both the event thread (submit, onScene,
 * onApprovalDecided) and the loop's virtual thread is marked {@code volatile}.
 */
public class AgentRuntime {

    private final System.Logger logger = System.getLogger(getClass().getName());

    private static final long SCENE_SETTLE_TIMEOUT_SECONDS = 5;

    private static final long LOOP_STOP_WAIT_MILLIS = 2000;

    /**
     * Grace window after an agent dispatch within which monitored events
     * (e.g. {@code SET_PRIMARY} fired by URL-routing as a scene rebuild
     * settles) are still treated as agent-driven. The framework dispatches
     * some events from queued tasks running on the command-loop thread,
     * past the synchronous dispatcher fence; the {@link ActionDispatcher}
     * thread-local cannot catch those. This grace covers them.
     */
    private static final long POST_DISPATCH_GRACE_MILLIS = 1000;

    /**
     * Default set of event keys monitored on the active contract's lookup
     * for user-driven interruption. When any of these events fires without
     * {@link ActionDispatcher#isAgentDispatch()} being set, the runtime
     * treats it as a user button action and applies the
     * {@link InterruptionPolicy}.
     */
    private static final List<EventKey<?>> DEFAULT_MONITORED_USER_EVENTS = List.of(
            EventKeys.SET_PRIMARY,
            ListViewContract.CREATE_ELEMENT_REQUESTED,
            ListViewContract.EDIT_ELEMENT_REQUESTED,
            ListViewContract.EDIT_SELECTED_REQUESTED,
            ListViewContract.BULK_DELETE_REQUESTED,
            ListViewContract.DELETE_SELECTED_REQUESTED,
            ListViewContract.PAGE_CHANGE_REQUESTED,
            ListViewContract.SELECT_ALL_REQUESTED,
            FormViewContract.FORM_SUBMITTED,
            FormViewContract.CANCEL_REQUESTED,
            FormViewContract.FORM_FIELD_SET,
            EditViewContract.DELETE_REQUESTED);

    private final AgentService agentService;
    private final ActionDispatcher dispatcher;
    private final AgentSpawner spawner;
    private final Authorization authorization;
    private final StructureNode structure;
    private final Lookup lookup;
    private final AgentFeedback feedback;
    private final LoopPolicy loopPolicy;
    private final InterruptionPolicy interruptionPolicy;
    private final String diagnosticLabel;

    // Touched from both the event thread (submit, onApprovalDecided) and
    // the loop's virtual thread. Volatile guarantees the cross-thread reads
    // see the latest assignment without locking.
    private volatile ActionGate gate;
    private volatile AgentActionFilter actionFilter;
    private volatile AgentSession agentSession;
    private volatile AgentResult queuedResult;
    private volatile String queuedUserText;
    private volatile PendingAction pendingConfirm;
    private volatile Scene currentScene;
    private volatile CompletableFuture<Scene> sceneSettleFuture;

    // Loop lifecycle: at most one loop runs at a time.
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile AbortToken currentToken;

    // Plan-step queue. Initialised at the start of {@link #runLoop} and only
    // touched on the loop's virtual thread thereafter (and the loop synchronises
    // implicitly with itself), so no volatility is required.
    private Deque<String> planQueue;
    private int planStepsConsumed;
    private int planStepsEnqueuedTotal;

    // User-event monitoring on the active contract's lookup. Subscriptions
    // are torn down and re-installed in {@link #onScene} whenever the routed
    // contract class changes. Accessed only from the event thread (onScene),
    // so no synchronisation is needed.
    private final List<Lookup.Registration> userEventMonitorRegistrations = new ArrayList<>();
    private Class<? extends ViewContract> monitoredContractClass;

    // Set true while the loop is inside dispatch + scene-settle. Cleared
    // shortly after; {@link #lastDispatchEndMillis} provides a grace window
    // for follow-on events arriving from the framework's async re-render.
    private volatile boolean agentDispatchActive;
    private volatile long lastDispatchEndMillis;

    private record PendingAction(ContractAction action, ContractActionPayload payload) {}

    public AgentRuntime(AgentService agentService,
                        ActionDispatcher dispatcher,
                        AgentSpawner spawner,
                        Authorization authorization,
                        StructureNode structure,
                        Lookup lookup,
                        AgentFeedback feedback,
                        LoopPolicy loopPolicy,
                        String diagnosticLabel) {
        this(agentService, dispatcher, spawner, authorization, structure, lookup,
                feedback, loopPolicy, InterruptionPolicy.strictStop(), diagnosticLabel);
    }

    public AgentRuntime(AgentService agentService,
                        ActionDispatcher dispatcher,
                        AgentSpawner spawner,
                        Authorization authorization,
                        StructureNode structure,
                        Lookup lookup,
                        AgentFeedback feedback,
                        LoopPolicy loopPolicy,
                        InterruptionPolicy interruptionPolicy,
                        String diagnosticLabel) {
        this.agentService = Objects.requireNonNull(agentService);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.spawner = Objects.requireNonNull(spawner);
        this.authorization = Objects.requireNonNull(authorization);
        this.structure = structure;
        this.lookup = Objects.requireNonNull(lookup);
        this.interruptionPolicy = Objects.requireNonNull(interruptionPolicy);
        this.feedback = Objects.requireNonNull(feedback);
        this.loopPolicy = Objects.requireNonNull(loopPolicy);
        this.diagnosticLabel = diagnosticLabel != null ? diagnosticLabel : "unknown";

        // Pre-spawn: deny-by-default gate; the loop's evaluateAndDispatch
        // escalates to the spawner when a denial is encountered without a session.
        this.agentSession = null;
        this.gate = (action, payload, lkp) -> new GateResult.Block("No active session");
        this.actionFilter = new PolicyActionFilter(authorization);
    }

    // ==================================================================
    // Host hooks
    // ==================================================================

    /**
     * Push the current scene from the host contract. Completes any pending
     * scene-settle future used by plan/loop navigation, and rebinds the
     * user-event monitor onto the new active contract's lookup so user
     * button actions can interrupt the running loop.
     */
    public void onScene(Scene scene) {
        this.currentScene = scene;
        CompletableFuture<Scene> future = this.sceneSettleFuture;
        if (future != null && !future.isDone()) {
            future.complete(scene);
        }
        rebindUserEventMonitor(scene);
    }

    /**
     * Install (or replace) subscribers on the active contract's lookup for
     * each event in {@link #DEFAULT_MONITORED_USER_EVENTS}. Subscribers fire
     * {@link #notifyEvent} with {@link EventOrigin#USER} when the publish was
     * NOT tagged by {@link ActionDispatcher#isAgentDispatch()} — i.e. the
     * event came from a user button action rather than from the agent itself.
     * <p>
     * The monitor is rebound only when the routed contract class changes, so
     * repeated {@link #onScene} pushes with the same routed contract are cheap.
     */
    private void rebindUserEventMonitor(Scene scene) {
        Class<? extends ViewContract> newClass =
                (scene != null && scene.routedContract() != null)
                        ? scene.routedContract().getClass() : null;
        Lookup activeLookup =
                (scene != null && scene.routedContract() != null)
                        ? scene.routedContract().lookup() : null;
        rebindUserEventMonitor(newClass, activeLookup);
    }

    /**
     * Variant accepting the class + lookup directly. Package-private so tests
     * can install monitoring without constructing a real {@link Scene}.
     */
    void rebindUserEventMonitor(Class<? extends ViewContract> newClass, Lookup activeLookup) {
        if (Objects.equals(newClass, monitoredContractClass)) {
            return;
        }
        for (Lookup.Registration reg : userEventMonitorRegistrations) {
            try {
                reg.unsubscribe();
            } catch (Throwable t) {
                logger.log(System.Logger.Level.WARNING,
                        "Failed to unsubscribe user-event monitor", t);
            }
        }
        userEventMonitorRegistrations.clear();
        monitoredContractClass = newClass;
        if (activeLookup == null) {
            return;
        }
        for (EventKey<?> key : DEFAULT_MONITORED_USER_EVENTS) {
            userEventMonitorRegistrations.add(installMonitor(activeLookup, key));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Lookup.Registration installMonitor(Lookup lookup, EventKey<?> key) {
        if (key instanceof EventKey.VoidKey vk) {
            return lookup.subscribe(vk, () -> handleMonitoredEvent(key));
        }
        EventKey.SimpleKey raw = (EventKey.SimpleKey) key;
        return lookup.subscribe(raw, (name, payload) -> handleMonitoredEvent(key));
    }

    private void handleMonitoredEvent(EventKey<?> key) {
        if (ActionDispatcher.isAgentDispatch()) {
            // Synchronously inside the dispatcher's publish — agent-driven.
            return;
        }
        if (agentDispatchActive) {
            // The loop is mid-dispatch on its vthread; events firing on
            // other threads (state-update tasks, re-renders) are aftermath
            // of the agent's own action, not user interactions.
            return;
        }
        if (System.currentTimeMillis() - lastDispatchEndMillis < POST_DISPATCH_GRACE_MILLIS) {
            // Late events arriving shortly after dispatch returned —
            // still part of the previous step's settle aftermath.
            return;
        }
        notifyEvent(EventOrigin.USER, key);
    }

    /**
     * Process a user prompt. Synchronously routes yes/no answers and pending-
     * approval responses; otherwise kicks off the agent loop on a virtual thread.
     */
    public void submit(String text) {
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("AgentRuntime@%x submit text='%s' [queuedResult=%s, pendingConfirm=%s, running=%s, label=%s]",
                                System.identityHashCode(this), abbreviate(text),
                                queuedResult != null, pendingConfirm != null,
                                running.get(), diagnosticLabel));

        if (queuedResult != null) {
            feedback.send("Still awaiting your approval for the previous request...");
            return;
        }

        String trimmed = text.trim().toLowerCase();

        // Yes/No routing for a pending confirmation. Both branches return early
        // and do NOT enter the loop. Any other input falls through to a new prompt
        // (and the pending confirmation remains set — matches pre-loop semantics).
        if (pendingConfirm != null) {
            if (trimmed.equals("yes") || trimmed.equals("y")) {
                PendingAction confirmed = pendingConfirm;
                pendingConfirm = null;
                DispatchResult result = dispatcher.dispatchDirect(
                    confirmed.action(), confirmed.payload(), activeContract());
                handleDispatchResult(result);
                return;
            }
            if (trimmed.equals("no") || trimmed.equals("n")) {
                pendingConfirm = null;
                feedback.send("Cancelled.");
                return;
            }
        }

        if (running.get()) {
            // A loop is already running. Treat the new submit as a USER event
            // and let the interruption policy decide. Default (strictStop)
            // cancels the old loop and runs the new prompt; never() rejects.
            if (interruptionPolicy.shouldStop(EventOrigin.USER, null)) {
                cancel();
                if (!awaitLoopStopped(LOOP_STOP_WAIT_MILLIS)) {
                    feedback.send("Could not interrupt previous prompt in time.");
                    return;
                }
            } else {
                feedback.send("Still processing previous prompt...");
                return;
            }
        }
        if (!running.compareAndSet(false, true)) {
            // Lost a race with another submit/post-approval starter. Bail.
            feedback.send("Still processing previous prompt...");
            return;
        }

        AbortToken token = new AbortToken();
        this.currentToken = token;
        final ViewContract initialContract = activeContract();
        feedback.send("<em>Thinking...</em>");
        final long startTime = System.currentTimeMillis();

        Thread.startVirtualThread(() -> {
            logger.log(System.Logger.Level.DEBUG,
                () -> String.format("AgentRuntime@%x loop START [label=%s]",
                                    System.identityHashCode(this), diagnosticLabel));
            try {
                runLoop(text, initialContract, token, startTime);
            } catch (Throwable t) {
                logger.log(System.Logger.Level.ERROR, "Loop crashed", t);
                feedback.send("Internal error: " + t.getClass().getSimpleName()
                                + (t.getMessage() != null ? " - " + t.getMessage() : ""));
            } finally {
                running.set(false);
                if (this.currentToken == token) {
                    this.currentToken = null;
                }
                logger.log(System.Logger.Level.DEBUG,
                    () -> String.format("AgentRuntime@%x loop END [label=%s]",
                                        System.identityHashCode(this), diagnosticLabel));
            }
        });
    }

    /**
     * Cancel the currently running loop, if any. The next iteration boundary
     * (and the {@link AgentService#handlePrompt} call, if it cooperates) will
     * observe the cancellation and stop. Safe to call from any thread.
     */
    public void cancel() {
        AbortToken token = this.currentToken;
        if (token != null) {
            token.cancel();
        }
    }

    /**
     * Notify the runtime of an external event so the configured
     * {@link InterruptionPolicy} can decide whether to interrupt the running
     * loop. Pass {@link EventOrigin#USER} for events that originate from
     * user interactions (clicks, form input, navigation); pass
     * {@link EventOrigin#AGENT} for events the agent itself produced.
     * <p>
     * The {@code key} is optional context for the policy. If the loop is not
     * running, this method is a no-op.
     */
    public void notifyEvent(EventOrigin origin, EventKey<?> key) {
        if (!running.get()) {
            return;
        }
        if (interruptionPolicy.shouldStop(origin, key)) {
            cancel();
        }
    }

    /**
     * Busy-wait (with short sleeps) until the running loop clears the
     * {@code running} flag or the timeout elapses.
     *
     * @return {@code true} if the loop stopped, {@code false} on timeout
     */
    private boolean awaitLoopStopped(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (running.get() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !running.get();
    }

    /**
     * Receive the user's decision on a delegation approval modal. On approval,
     * a fresh spawn is requested and any queued result is fed into a kickstart
     * loop — bootstrapping {@link #runLoop} with the result so any enqueued
     * plan steps actually iterate to completion.
     */
    public void onApprovalDecided(boolean approved) {
        AgentResult queued = this.queuedResult;
        String queuedText = this.queuedUserText;
        this.queuedResult = null;
        this.queuedUserText = null;
        if (!approved) {
            feedback.send("Agent delegation denied.");
            return;
        }
        SpawnResult retry = this.spawner.spawn(
                new SpawnRequest(AgentContext.Scope.APP, ControlMode.ASSIST, null), lookup);
        if (retry instanceof SpawnResult.Approved a) {
            installSession(a.session());
            feedback.send("Agent access approved.");
            if (queued != null) {
                startLoopFromApproval(queued, queuedText, activeContract());
            }
        } else {
            feedback.send("Agent session could not be established.");
        }
    }

    private void startLoopFromApproval(AgentResult queued, String userText, ViewContract capturedContract) {
        // The original submit's loop has already returned (after queueing),
        // so {@code running} should be false. Defensive guard for unexpected state.
        if (!running.compareAndSet(false, true)) {
            feedback.send("Internal error: agent runtime busy after approval.");
            return;
        }
        AbortToken token = new AbortToken();
        this.currentToken = token;
        final long startTime = System.currentTimeMillis();
        Thread.startVirtualThread(() -> {
            try {
                runLoop(userText, capturedContract, token, startTime, queued);
            } catch (Throwable t) {
                logger.log(System.Logger.Level.ERROR, "Post-approval loop failed", t);
                feedback.send("Internal error: " + t.getClass().getSimpleName());
            } finally {
                running.set(false);
                if (this.currentToken == token) {
                    this.currentToken = null;
                }
            }
        });
    }

    // ==================================================================
    // Loop
    // ==================================================================

    /**
     * Run the agent loop for a user prompt.
     * <p>
     * Iteration model — strictly queue-driven:
     * <ol>
     *   <li>If the LLM returns a {@code PlanResult}, its steps are enqueued
     *       and the loop iterates through them, dispatching each as an
     *       ordinary step. Plan-step dispatches count toward the budget.</li>
     *   <li>If the LLM returns any other dispatchable result while the queue
     *       is <em>empty</em> (i.e. not driven by a plan), the loop dispatches
     *       once and stops. Without progress context, re-prompting with the
     *       same user text would loop on the same intent — a real "reactive"
     *       loop requires step-history plumbing into the prompt and is
     *       deferred to a future phase.</li>
     * </ol>
     * The queue and counters are reset on each invocation so the runtime is
     * reusable across submits. Package-private to allow direct unit testing.
     */
    void runLoop(String userText, ViewContract initialContract, AbortToken token, long startTime) {
        runLoop(userText, initialContract, token, startTime, null);
    }

    /**
     * Run the loop, optionally bootstrapped with a {@code kickstart} result.
     * When non-null, the first iteration uses {@code kickstart} instead of
     * calling the LLM — used by the post-approval path so a queued
     * {@code PlanResult} actually iterates to completion.
     */
    void runLoop(String userText, ViewContract initialContract, AbortToken token,
                 long startTime, AgentResult kickstart) {
        int step = 0;
        boolean hasRun = false;
        boolean followupAllowed = false;
        boolean followupConsumed = false;
        ViewContract capturedContract = initialContract;
        this.planQueue = new ArrayDeque<>();
        this.planStepsConsumed = 0;
        this.planStepsEnqueuedTotal = 0;
        AgentResult pendingKickstart = kickstart;

        while (true) {
            if (token.isCancelled()) {
                final int cancelledAt = step;
                logger.log(System.Logger.Level.DEBUG,
                    () -> String.format("AgentRuntime@%x loop cancelled at step %d", System.identityHashCode(this), cancelledAt));
                return;
            }

            // Iteration source: kickstart (bootstrap), queued plan step, the
            // original user text on first iteration, or — once exactly — a
            // re-prompt after a scene-changing non-queue dispatch to give the
            // LLM a chance to plan from the new scene. The follow-up is
            // bounded at one consumption (tracked by {@code followupConsumed})
            // to prevent navigate→navigate→… cascades.
            boolean fromQueue;
            AgentResult result;
            if (pendingKickstart != null) {
                result = pendingKickstart;
                pendingKickstart = null;
                fromQueue = false;
                capturedContract = activeContract();
            } else {
                String prompt;
                if (!planQueue.isEmpty()) {
                    prompt = planQueue.pollFirst();
                    planStepsConsumed++;
                    feedback.send("<em>Step " + planStepsConsumed + "/" + planStepsEnqueuedTotal
                            + ": " + HtmlEscape.escape(prompt) + "</em>");
                    fromQueue = true;
                } else if (!hasRun) {
                    prompt = userText;
                    fromQueue = false;
                } else if (followupAllowed) {
                    prompt = userText;
                    fromQueue = false;
                    followupAllowed = false;
                    followupConsumed = true;
                } else {
                    return;
                }

                AgentContext agentContext = buildAgentContext();
                ContractProfile profile = agentContext.contractProfile();
                capturedContract = activeContract();

                final String promptForLambda = prompt;
                try {
                    result = agentService.handlePrompt(promptForLambda, profile, structure,
                            partial -> {
                                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                                feedback.updateLast("<em>Thinking... (" + elapsed + "s)</em>");
                            },
                            token);
                } catch (Throwable t) {
                    logger.log(System.Logger.Level.ERROR, "LLM call failed", t);
                    feedback.send("Internal error: " + t.getClass().getSimpleName()
                                    + (t.getMessage() != null ? " - " + t.getMessage() : ""));
                    return;
                }

                if (token.isCancelled()) {
                    return;
                }
            }
            hasRun = true;

            final int currentStep = step + 1;
            final AgentResult dispatched = result;
            logger.log(System.Logger.Level.DEBUG,
                () -> String.format("AgentRuntime@%x step %d: %s",
                                    System.identityHashCode(this), currentStep,
                                    dispatched.getClass().getSimpleName()));

            agentDispatchActive = true;
            boolean continuable;
            try {
                continuable = evaluateAndDispatch(result, capturedContract, userText);
            } finally {
                lastDispatchEndMillis = System.currentTimeMillis();
                agentDispatchActive = false;
            }
            if (!continuable) {
                return;
            }

            // PlanResult only enqueues; loop body runs again to pop the first
            // step. Plan classification does not count toward budget.
            if (result instanceof AgentResult.PlanResult) {
                continue;
            }

            // Non-plan dispatch from outside the queue: usually single-shot.
            // Exception: when the dispatch changed the scene AND we haven't
            // used the follow-up yet, allow one re-prompt so the LLM can see
            // the new scene and decide (typically to plan a sequence of
            // set_field steps on a freshly opened form). Bounded at one —
            // {@code followupConsumed} prevents navigate→navigate cascades.
            if (!fromQueue) {
                if (!followupConsumed && changesSceneFor(result)) {
                    followupAllowed = true;
                    continue;
                }
                return;
            }

            step++;
            if (!loopPolicy.shouldContinue(result, step)) {
                feedback.send("<em>Reached step budget (" + step + "); stopping.</em>");
                return;
            }
        }
    }

    /**
     * @return true if the result is a navigation or an action declared with
     *         {@link DispatchEffect#SCENE_CHANGE} — i.e. an iteration that
     *         alters the routed contract and may warrant a follow-up reaction.
     */
    private static boolean changesSceneFor(AgentResult result) {
        if (result instanceof AgentResult.NavigateResult) return true;
        if (result instanceof AgentResult.ActionResult ar) {
            return ar.action().effect() == DispatchEffect.SCENE_CHANGE;
        }
        return false;
    }

    /**
     * Evaluate one LLM result against the active authorization, escalating to
     * the spawner on a pre-session denial. Returns {@code true} if the loop
     * should continue (a dispatchable action ran), {@code false} for any
     * terminal state (text reply, plan, blocked, awaiting confirm, awaiting
     * approval, denied).
     */
    private boolean evaluateAndDispatch(AgentResult result, ViewContract capturedContract, String userText) {
        Authorization current = (agentSession != null && agentSession.isValid())
                ? authorization.delegated(agentSession.grant())
                : authorization;
        AccessDecision decision = current.evaluate(attrsFor(result));

        if (decision instanceof AccessDecision.Allow) {
            return executeStep(result, capturedContract);
        }
        if (agentSession != null && agentSession.isValid()) {
            String reason = (decision instanceof AccessDecision.Deny d) ? d.reason() : "denied";
            feedback.send("Action not permitted: " + reason);
            return false;
        }
        // No grant yet — request one via the spawner.
        SpawnRequest request = new SpawnRequest(
                AgentContext.Scope.APP, ControlMode.ASSIST, describe(result));
        SpawnResult spawn = spawner.spawn(request, lookup);
        return switch (spawn) {
            case SpawnResult.Approved a -> {
                installSession(a.session());
                yield executeStep(result, capturedContract);
            }
            case SpawnResult.RequiresApproval _ -> {
                this.queuedResult = result;
                this.queuedUserText = userText;
                lookup.publish(EventKeys.SHOW, new ActionBindings.ShowPayload(
                        DelegationApprovalContract.class,
                        Map.of("scope", request.scope().name(),
                               "controlMode", request.controlMode().name(),
                               "reason", describe(result))));
                feedback.send("This requires your approval...");
                yield false;
            }
            case SpawnResult.Denied d -> {
                feedback.send("Agent session denied: " + d.reason());
                yield false;
            }
        };
    }

    /**
     * Dispatch one LLM result. Returns {@code true} if the loop may proceed
     * to a next iteration, {@code false} otherwise.
     */
    private boolean executeStep(AgentResult result, ViewContract capturedContract) {
        final ActionGate capturedGate = gate;
        switch (result) {
            case AgentResult.TextReply reply -> {
                feedback.send(HtmlEscape.escape(reply.message()).replace("\n", "<br/>"));
                return false;
            }
            case AgentResult.NavigateResult nav -> {
                if (currentScene != null && currentScene.routedContract() != null
                        && nav.targetContract().isInstance(currentScene.routedContract())) {
                    feedback.send("Already on " + nav.targetContract().getSimpleName());
                    return true;
                }
                sceneSettleFuture = new CompletableFuture<>();
                dispatcher.dispatchNavigate(nav.targetContract(), lookup);
                feedback.send("Navigating...");
                Scene settled = awaitSceneSettle();
                if (settled == null) {
                    return false;
                }
                if (settled.routedContract() == null
                        || !nav.targetContract().isInstance(settled.routedContract())) {
                    feedback.send("Loop interrupted: unexpected navigation target.");
                    return false;
                }
                return true;
            }
            case AgentResult.ActionResult actionResult -> {
                DispatchResult dr = dispatcher.dispatch(
                    actionResult.action(), actionResult.payload(),
                    capturedContract, lookup, capturedGate);
                return handleDispatchResultForLoop(dr);
            }
            case AgentResult.PlanResult plan -> {
                if (!plan.summary().isBlank()) {
                    feedback.send(HtmlEscape.escape(plan.summary()));
                }
                List<String> newSteps = plan.steps();
                // Prepend in reverse so the plan's first step ends up at the
                // head of the queue. For a nested plan, this places sub-steps
                // immediately ahead of the remainder of the parent plan.
                for (int i = newSteps.size() - 1; i >= 0; i--) {
                    planQueue.addFirst(newSteps.get(i));
                }
                planStepsEnqueuedTotal += newSteps.size();
                feedback.send("<em>Plan: " + planStepsEnqueuedTotal + " steps</em>");
                return true;
            }
        }
    }

    /**
     * Loop-aware dispatch handling: returns whether iteration may continue
     * after the dispatch outcome.
     * <p>
     * For actions declared as {@link DispatchEffect#SCENE_CHANGE}, the runtime
     * arms {@link #sceneSettleFuture} before dispatch and waits for it to
     * complete after the dispatcher's processed-fence — so the next iteration
     * sees the rebuilt scene. The wait only happens when more plan steps
     * remain (single-shot dispatches don't need the next context). A timeout
     * means the scene didn't rebuild as declared; treated as a hard error.
     */
    private boolean handleDispatchResultForLoop(DispatchResult result) {
        return switch (result) {
            case DispatchResult.Dispatched d -> {
                boolean awaitSceneChange = d.action().effect() == DispatchEffect.SCENE_CHANGE
                        && planQueue != null
                        && !planQueue.isEmpty();
                if (awaitSceneChange) {
                    // Arm before dispatch so onScene completes the future even if
                    // the rebuild races with our processed.join below.
                    sceneSettleFuture = new CompletableFuture<>();
                }
                feedback.send(d.action().description());
                try {
                    d.processed().join();
                } catch (Throwable t) {
                    feedback.send("Dispatch wait failed: " + t.getClass().getSimpleName());
                    yield false;
                }
                if (awaitSceneChange && !awaitSceneSettled()) {
                    // Timeout — awaitSceneSettled already emitted the
                    // diagnostic. Stop the loop: the next iteration would
                    // run against stale context and almost certainly fail.
                    yield false;
                }
                yield true;
            }
            case DispatchResult.Blocked b -> {
                feedback.send(b.reason());
                yield false;
            }
            case DispatchResult.AwaitingConfirmation c -> {
                // Assign state BEFORE emitting feedback so a caller awaiting the
                // confirmation message observes a consistent (state, message) pair.
                pendingConfirm = new PendingAction(c.action(), c.payload());
                feedback.send(c.question());
                yield false;
            }
            case DispatchResult.PayloadError pe -> {
                feedback.send("Invalid payload for '" + HtmlEscape.escape(pe.action())
                    + "': " + HtmlEscape.escape(pe.message()));
                yield false;
            }
        };
    }

    /**
     * Synchronous dispatch handler — for the yes-confirmation path
     * (no loop accounting, no return value).
     */
    private void handleDispatchResult(DispatchResult result) {
        switch (result) {
            case DispatchResult.Dispatched d ->
                feedback.send(d.action().description());
            case DispatchResult.Blocked b ->
                feedback.send(b.reason());
            case DispatchResult.AwaitingConfirmation c -> {
                pendingConfirm = new PendingAction(c.action(), c.payload());
                feedback.send(c.question());
            }
            case DispatchResult.PayloadError pe ->
                feedback.send("Invalid payload for '" + HtmlEscape.escape(pe.action())
                    + "': " + HtmlEscape.escape(pe.message()));
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private Attributes attrsFor(AgentResult result) {
        Attributes.Builder b = Attributes.builder()
                .put(AttributeKeys.CONTROL_CHANNEL, "agent_intent");
        return switch (result) {
            case AgentResult.TextReply _ -> b
                .put(AttributeKeys.ACTION_NAME, "chat:reply")
                .put(AttributeKeys.ACTION_TYPE, "chat").build();
            case AgentResult.NavigateResult nav -> b
                .put(AttributeKeys.ACTION_NAME, "navigate")
                .put(AttributeKeys.ACTION_TYPE, "navigate")
                .put(AttributeKeys.RESOURCE_CONTRACT_CLASS, nav.targetContract().getName()).build();
            case AgentResult.ActionResult ar -> b
                .put(AttributeKeys.ACTION_NAME, ar.action().action())
                .put(AttributeKeys.ACTION_TYPE, "execute").build();
            case AgentResult.PlanResult _ -> b
                .put(AttributeKeys.ACTION_NAME, "execute_plan")
                .put(AttributeKeys.ACTION_TYPE, "execute").build();
        };
    }

    private String describe(AgentResult result) {
        return switch (result) {
            case AgentResult.TextReply _ -> "Reply to user";
            case AgentResult.NavigateResult nav ->
                    "Navigate to " + nav.targetContract().getSimpleName();
            case AgentResult.ActionResult ar ->
                    "Execute action: " + ar.action().action();
            case AgentResult.PlanResult plan ->
                    plan.summary().isBlank() ? "Execute plan" : "Execute plan: " + plan.summary();
        };
    }

    private void installSession(AgentSession session) {
        this.agentSession = session;
        Authorization agentAuth = authorization.delegated(session.grant());
        this.gate = new PolicyGate(agentAuth);
        this.actionFilter = new PolicyActionFilter(agentAuth);
    }

    private Scene awaitSceneSettle() {
        CompletableFuture<Scene> future = sceneSettleFuture;
        if (future == null) return currentScene;
        try {
            Scene settled = future.get(SCENE_SETTLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            sceneSettleFuture = null;
            return settled;
        } catch (TimeoutException e) {
            feedback.send("Loop interrupted: scene did not settle in time.");
            return null;
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Boolean variant: true if {@link #sceneSettleFuture} completed within the
     * timeout (regardless of the value it carried — onScene may legitimately
     * deliver a null Scene), false on timeout. Used by the dispatch-effect
     * gate where the caller only needs to know "the rebuild signaled."
     */
    private boolean awaitSceneSettled() {
        CompletableFuture<Scene> future = sceneSettleFuture;
        if (future == null) return true;
        try {
            future.get(SCENE_SETTLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            sceneSettleFuture = null;
            return true;
        } catch (TimeoutException e) {
            feedback.send("Loop interrupted: scene did not settle in time.");
            return false;
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private AgentContext buildAgentContext() {
        ViewContract active = currentScene != null ? currentScene.routedContract() : null;
        return AgentContext.forScope(AgentContext.Scope.APP, active, structure, actionFilter, lookup);
    }

    private ViewContract activeContract() {
        return currentScene != null ? currentScene.routedContract() : null;
    }

    private static String abbreviate(String s) {
        if (s == null) return "null";
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= 60 ? oneLine : oneLine.substring(0, 57) + "...";
    }
}
