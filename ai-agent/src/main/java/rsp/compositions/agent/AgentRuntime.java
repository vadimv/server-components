package rsp.compositions.agent;

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
import rsp.compositions.contract.EventKeys;
import rsp.compositions.contract.Scene;
import rsp.compositions.contract.ViewContract;
import rsp.util.html.HtmlEscape;

import java.util.ArrayDeque;
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

    private final AgentService agentService;
    private final ActionDispatcher dispatcher;
    private final AgentSpawner spawner;
    private final Authorization authorization;
    private final StructureNode structure;
    private final Lookup lookup;
    private final AgentFeedback feedback;
    private final LoopPolicy loopPolicy;
    private final String diagnosticLabel;

    // Touched from both the event thread (submit, onApprovalDecided) and
    // the loop's virtual thread. Volatile guarantees the cross-thread reads
    // see the latest assignment without locking.
    private volatile ActionGate gate;
    private volatile AgentActionFilter actionFilter;
    private volatile AgentSession agentSession;
    private volatile AgentResult queuedResult;
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
        this.agentService = Objects.requireNonNull(agentService);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.spawner = Objects.requireNonNull(spawner);
        this.authorization = Objects.requireNonNull(authorization);
        this.structure = structure;
        this.lookup = Objects.requireNonNull(lookup);
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
     * scene-settle future used by plan/loop navigation.
     */
    public void onScene(Scene scene) {
        this.currentScene = scene;
        CompletableFuture<Scene> future = this.sceneSettleFuture;
        if (future != null && !future.isDone()) {
            future.complete(scene);
        }
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

        if (!running.compareAndSet(false, true)) {
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
     * Receive the user's decision on a delegation approval modal. On approval,
     * a fresh spawn is requested and any queued result is fed into a kickstart
     * loop — bootstrapping {@link #runLoop} with the result so any enqueued
     * plan steps actually iterate to completion.
     */
    public void onApprovalDecided(boolean approved) {
        AgentResult queued = this.queuedResult;
        this.queuedResult = null;
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
                startLoopFromApproval(queued, activeContract());
            }
        } else {
            feedback.send("Agent session could not be established.");
        }
    }

    private void startLoopFromApproval(AgentResult queued, ViewContract capturedContract) {
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
                runLoop(null, capturedContract, token, startTime, queued);
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

            // Iteration source: kickstart (bootstrap), queued plan step, or
            // the original user text (first iteration only). Once at least one
            // iteration has run and the queue is empty, return — no closing
            // re-prompt (which could cascade if the LLM re-issues the plan).
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

            boolean continuable = evaluateAndDispatch(result, capturedContract);
            if (!continuable) {
                return;
            }

            // PlanResult only enqueues; loop body runs again to pop the first
            // step. Plan classification does not count toward budget.
            if (result instanceof AgentResult.PlanResult) {
                continue;
            }

            // Non-plan dispatch from outside the queue: single-shot, stop here.
            // Continuing would re-prompt with identical user text and loop on
            // the same intent (no progress context to break the cycle).
            if (!fromQueue) {
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
     * Evaluate one LLM result against the active authorization, escalating to
     * the spawner on a pre-session denial. Returns {@code true} if the loop
     * should continue (a dispatchable action ran), {@code false} for any
     * terminal state (text reply, plan, blocked, awaiting confirm, awaiting
     * approval, denied).
     */
    private boolean evaluateAndDispatch(AgentResult result, ViewContract capturedContract) {
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
                feedback.send(HtmlEscape.escape(reply.message()));
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
     */
    private boolean handleDispatchResultForLoop(DispatchResult result) {
        return switch (result) {
            case DispatchResult.Dispatched d -> {
                feedback.send(d.action().description());
                try {
                    d.processed().join();
                } catch (Throwable t) {
                    feedback.send("Dispatch wait failed: " + t.getClass().getSimpleName());
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
