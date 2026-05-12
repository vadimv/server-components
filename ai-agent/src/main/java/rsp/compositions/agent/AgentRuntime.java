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

import java.util.ArrayList;
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
    private static final int MAX_PLAN_STEPS = 20;

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
     * a fresh spawn is requested and any queued result is executed as a
     * single step (not looped — the queued result is a snapshot of the
     * original LLM intent).
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
                final ViewContract capturedContract = activeContract();
                // Single-shot post-approval execution. The original goal that
                // led to this approval is lost — re-prompt to resume agentic flow.
                Thread.startVirtualThread(() -> {
                    try {
                        executeStep(queued, capturedContract);
                    } catch (Throwable t) {
                        logger.log(System.Logger.Level.ERROR,
                                "Post-approval execution failed", t);
                        feedback.send("Internal error: " + t.getClass().getSimpleName());
                    }
                });
            }
        } else {
            feedback.send("Agent session could not be established.");
        }
    }

    // ==================================================================
    // Loop
    // ==================================================================

    /**
     * Run the agent loop for a user prompt. Re-prompts the LLM with the same
     * goal on each iteration, re-materialising context from the current scene.
     * <p>
     * Package-private to allow direct unit testing without spinning up
     * the virtual thread machinery.
     */
    void runLoop(String userText, ViewContract initialContract, AbortToken token, long startTime) {
        int step = 0;
        ViewContract capturedContract = initialContract;

        while (true) {
            if (token.isCancelled()) {
                final int cancelledAt = step;
                logger.log(System.Logger.Level.DEBUG,
                    () -> String.format("AgentRuntime@%x loop cancelled at step %d", System.identityHashCode(this), cancelledAt));
                return;
            }

            AgentContext agentContext = buildAgentContext();
            ContractProfile profile = agentContext.contractProfile();
            capturedContract = activeContract();

            AgentResult result;
            try {
                result = agentService.handlePrompt(userText, profile, structure,
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
                executePlan(plan.steps());
                // Plan executor runs its own multi-step iteration internally;
                // the outer loop does not continue once a plan completes.
                return false;
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

    private void executePlan(List<String> originalSteps) {
        List<String> steps = new ArrayList<>(originalSteps);
        int totalSteps = steps.size();
        feedback.send("<em>Plan: " + totalSteps + " steps</em>");

        for (int i = 0; i < steps.size(); i++) {
            String step = steps.get(i);
            feedback.send("<em>Step " + (i + 1) + "/" + totalSteps + ": "
                            + HtmlEscape.escape(step) + "</em>");

            final Class<?> preStepContract = routedContractClass();

            AgentContext agentContext = buildAgentContext();
            ContractProfile freshProfile = agentContext.contractProfile();
            final ViewContract capturedContract = activeContract();
            final ActionGate capturedGate = gate;

            AgentResult stepResult = agentService.handlePrompt(step, freshProfile, structure);

            if (!Objects.equals(preStepContract, routedContractClass())) {
                feedback.send("Plan interrupted: scene changed during step execution.");
                return;
            }

            switch (stepResult) {
                case AgentResult.TextReply reply -> {
                    feedback.send(HtmlEscape.escape(reply.message()));
                    return;
                }
                case AgentResult.NavigateResult nav -> {
                    if (currentScene != null && currentScene.routedContract() != null
                            && nav.targetContract().isInstance(currentScene.routedContract())) {
                        feedback.send("Already on " + nav.targetContract().getSimpleName());
                    } else {
                        sceneSettleFuture = new CompletableFuture<>();
                        dispatcher.dispatchNavigate(nav.targetContract(), lookup);
                        feedback.send("Navigating...");

                        Scene settled = awaitSceneSettle();
                        if (settled == null) {
                            return;
                        }
                        if (settled.routedContract() == null
                                || !nav.targetContract().isInstance(settled.routedContract())) {
                            feedback.send("Plan interrupted: unexpected navigation.");
                            return;
                        }
                    }
                }
                case AgentResult.ActionResult actionResult -> {
                    DispatchResult dispatchResult = dispatcher.dispatch(
                            actionResult.action(), actionResult.payload(),
                            capturedContract, lookup, capturedGate);
                    switch (dispatchResult) {
                        case DispatchResult.Dispatched d -> {
                            feedback.send(d.action().description());
                            d.processed().join();
                        }
                        case DispatchResult.Blocked b -> {
                            feedback.send("Step blocked: " + b.reason());
                            return;
                        }
                        case DispatchResult.AwaitingConfirmation c -> {
                            feedback.send("Plan paused: action requires confirmation. " + c.question());
                            return;
                        }
                        case DispatchResult.PayloadError pe -> {
                            feedback.send("Step failed: " + HtmlEscape.escape(pe.message()));
                            return;
                        }
                    }
                }
                case AgentResult.PlanResult nested -> {
                    steps.addAll(i + 1, nested.steps());
                    totalSteps = steps.size();
                    if (totalSteps > MAX_PLAN_STEPS) {
                        feedback.send("Plan too large (" + totalSteps + " steps); aborting.");
                        return;
                    }
                }
            }
        }

        feedback.send("<em>Plan complete (" + totalSteps + " steps executed)</em>");
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

    private Class<?> routedContractClass() {
        Scene scene = currentScene;
        return (scene != null && scene.routedContract() != null)
                ? scene.routedContract().getClass() : null;
    }

    private static String abbreviate(String s) {
        if (s == null) return "null";
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= 60 ? oneLine : oneLine.substring(0, 57) + "...";
    }
}
