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

/**
 * Orchestrates LLM calls, authorization, action dispatch and plan execution
 * on behalf of a host contract (e.g. {@code PromptContract}).
 * <p>
 * The runtime is presentation-agnostic: status updates flow through {@link AgentFeedback}.
 * The host contract supplies the active {@link Scene} via {@link #onScene(Scene)} and
 * routes user input via {@link #submit(String)}.
 * <p>
 * State held: agent session + gate + filter, queued result awaiting approval, pending
 * yes/no confirmation, last observed scene, and a one-shot future used to await scene
 * settlement during plan execution.
 */
public class AgentRuntime {

    private final System.Logger logger = System.getLogger(getClass().getName());

    private static final int MAX_PLAN_STEPS = 20;
    private static final long SCENE_SETTLE_TIMEOUT_SECONDS = 5;

    private final AgentService agentService;
    private final ActionDispatcher dispatcher;
    private final AgentSpawner spawner;
    private final Authorization authorization;
    private final StructureNode structure;
    private final Lookup lookup;
    private final AgentFeedback feedback;
    private final String diagnosticLabel;

    private ActionGate gate;
    private AgentActionFilter actionFilter;
    private AgentSession agentSession;
    private AgentResult queuedResult;
    private PendingAction pendingConfirm;

    private volatile Scene currentScene;
    private volatile CompletableFuture<Scene> sceneSettleFuture;

    private record PendingAction(ContractAction action, ContractActionPayload payload) {}

    public AgentRuntime(AgentService agentService,
                        ActionDispatcher dispatcher,
                        AgentSpawner spawner,
                        Authorization authorization,
                        StructureNode structure,
                        Lookup lookup,
                        AgentFeedback feedback,
                        String diagnosticLabel) {
        this.agentService = Objects.requireNonNull(agentService);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.spawner = Objects.requireNonNull(spawner);
        this.authorization = Objects.requireNonNull(authorization);
        this.structure = structure;
        this.lookup = Objects.requireNonNull(lookup);
        this.feedback = Objects.requireNonNull(feedback);
        this.diagnosticLabel = diagnosticLabel != null ? diagnosticLabel : "unknown";

        // Defer spawn until the policy denies an action-bearing result.
        // Pre-spawn: chat/discover are auto-allowed by the policy; everything
        // else escalates to spawner.spawn(...) lazily in evaluateAndExecute.
        this.agentSession = null;
        this.gate = (action, payload, lkp) -> new GateResult.Block("No active session");
        this.actionFilter = new PolicyActionFilter(authorization);
    }

    /**
     * Push the current scene from the host contract. Completes any pending
     * scene-settle future used by plan navigation.
     */
    public void onScene(Scene scene) {
        this.currentScene = scene;
        CompletableFuture<Scene> future = this.sceneSettleFuture;
        if (future != null && !future.isDone()) {
            future.complete(scene);
        }
    }

    /**
     * Process a user prompt. If a yes/no confirmation is pending, a matching
     * answer dispatches the previously gated action; otherwise the prompt is
     * sent to the LLM and the result is evaluated against authorization.
     */
    public void submit(String text) {
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("AgentRuntime@%x submit text='%s' [queuedResult=%s, pendingConfirm=%s, label=%s]",
                                System.identityHashCode(this), abbreviate(text),
                                queuedResult != null, pendingConfirm != null, diagnosticLabel));
        if (queuedResult != null) {
            feedback.send("Still awaiting your approval for the previous request...");
            return;
        }

        String trimmed = text.trim().toLowerCase();

        // Handle pending confirmation (yes/no after AwaitingConfirmation)
        if (pendingConfirm != null) {
            if (trimmed.equals("yes") || trimmed.equals("y")) {
                PendingAction confirmed = pendingConfirm;
                pendingConfirm = null;
                ViewContract activeContract = activeContract();
                DispatchResult result = dispatcher.dispatchDirect(
                    confirmed.action(), confirmed.payload(), activeContract);
                handleDispatchResult(result);
                return;
            }
            if (trimmed.equals("no") || trimmed.equals("n")) {
                pendingConfirm = null;
                feedback.send("Cancelled.");
                return;
            }
        }

        // Run the LLM under whatever authorization currently applies.
        // Classification (chat vs action) is the LLM's response type, then
        // the policy decides whether it's auto-allowed or needs approval.
        AgentContext agentContext = buildAgentContext();
        ContractProfile profile = agentContext.contractProfile();
        final ViewContract capturedContract = activeContract();

        final long startTime = System.currentTimeMillis();
        feedback.send("<em>Thinking...</em>");
        Thread.startVirtualThread(() -> {
            logger.log(System.Logger.Level.DEBUG,
                () -> String.format("AgentRuntime@%x LLM virtual thread START [label=%s]",
                                    System.identityHashCode(this), diagnosticLabel));
            try {
                AgentResult agentResult = agentService.handlePrompt(text, profile, structure,
                        partial -> {
                            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                            feedback.updateLast("<em>Thinking... (" + elapsed + "s)</em>");
                        });
                logger.log(System.Logger.Level.DEBUG,
                    () -> String.format("AgentRuntime@%x LLM result received: %s [label=%s, elapsedMs=%d]",
                                        System.identityHashCode(this),
                                        agentResult.getClass().getSimpleName(), diagnosticLabel,
                                        System.currentTimeMillis() - startTime));
                evaluateAndExecute(agentResult, capturedContract);
            } catch (Throwable t) {
                logger.log(System.Logger.Level.ERROR, "Prompt processing failed", t);
                feedback.send("Internal error: " + t.getClass().getSimpleName()
                                + (t.getMessage() != null ? " - " + t.getMessage() : ""));
            } finally {
                logger.log(System.Logger.Level.DEBUG,
                    () -> String.format("AgentRuntime@%x LLM virtual thread END [label=%s]",
                                        System.identityHashCode(this), diagnosticLabel));
            }
        });
    }

    /**
     * Receive the user's decision on a delegation approval modal. On approval,
     * a fresh spawn is requested and any queued result is executed.
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
                // Run on a virtual thread — executePlan/awaitSceneSettle would
                // otherwise block the event loop and starve the very onScene
                // that signals scene settlement.
                final ViewContract capturedContract = activeContract();
                Thread.startVirtualThread(() -> {
                    try {
                        executeResult(queued, capturedContract);
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

    /**
     * Evaluate the LLM's result against the active authorization. If allowed,
     * execute. If denied because no grant is present yet, escalate to the
     * spawner — which surfaces the delegation approval modal.
     */
    private void evaluateAndExecute(AgentResult result, ViewContract capturedContract) {
        Authorization current = (agentSession != null && agentSession.isValid())
                ? authorization.delegated(agentSession.grant())
                : authorization;
        AccessDecision decision = current.evaluate(attrsFor(result));
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("AgentRuntime@%x evaluateAndExecute: %s -> %s [label=%s, hasSession=%s]",
                                System.identityHashCode(this), result.getClass().getSimpleName(),
                                decision.getClass().getSimpleName(), diagnosticLabel,
                                agentSession != null && agentSession.isValid()));

        if (decision instanceof AccessDecision.Allow) {
            executeResult(result, capturedContract);
            return;
        }
        if (agentSession != null && agentSession.isValid()) {
            // Grant is present and policy still denies — real refusal.
            String reason = (decision instanceof AccessDecision.Deny d) ? d.reason() : "denied";
            feedback.send("Action not permitted: " + reason);
            return;
        }
        // No grant yet — request one via the spawner.
        SpawnRequest request = new SpawnRequest(
                AgentContext.Scope.APP, ControlMode.ASSIST, describe(result));
        SpawnResult spawn = spawner.spawn(request, lookup);
        switch (spawn) {
            case SpawnResult.Approved a -> {
                installSession(a.session());
                executeResult(result, capturedContract);
            }
            case SpawnResult.RequiresApproval _ -> {
                this.queuedResult = result;
                lookup.publish(EventKeys.SHOW, new ActionBindings.ShowPayload(
                        DelegationApprovalContract.class,
                        Map.of("scope", request.scope().name(),
                               "controlMode", request.controlMode().name(),
                               "reason", describe(result))));
                feedback.send("This requires your approval...");
            }
            case SpawnResult.Denied d ->
                feedback.send("Agent session denied: " + d.reason());
        }
    }

    private void executeResult(AgentResult result, ViewContract capturedContract) {
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("AgentRuntime@%x executeResult: %s [label=%s]",
                                System.identityHashCode(this),
                                result.getClass().getSimpleName(), diagnosticLabel));
        final ActionGate capturedGate = gate;
        switch (result) {
            case AgentResult.TextReply reply ->
                feedback.send(HtmlEscape.escape(reply.message()));
            case AgentResult.NavigateResult nav -> {
                dispatcher.dispatchNavigate(nav.targetContract(), lookup);
                feedback.send("Navigating...");
            }
            case AgentResult.ActionResult actionResult -> {
                ContractAction action = actionResult.action();
                DispatchResult dr = dispatcher.dispatch(
                    action, actionResult.payload(), capturedContract, lookup, capturedGate);
                handleDispatchResult(dr);
            }
            case AgentResult.PlanResult plan -> {
                if (!plan.summary().isBlank()) {
                    feedback.send(HtmlEscape.escape(plan.summary()));
                }
                executePlan(plan.steps());
            }
        }
    }

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

    private void handleDispatchResult(DispatchResult result) {
        switch (result) {
            case DispatchResult.Dispatched d ->
                feedback.send(d.action().description());
            case DispatchResult.Blocked b ->
                feedback.send(b.reason());
            case DispatchResult.AwaitingConfirmation c -> {
                feedback.send(c.question());
                pendingConfirm = new PendingAction(c.action(), c.payload());
            }
            case DispatchResult.PayloadError pe ->
                feedback.send("Invalid payload for '" + HtmlEscape.escape(pe.action())
                    + "': " + HtmlEscape.escape(pe.message()));
        }
    }

    private void executePlan(List<String> originalSteps) {
        List<String> steps = new ArrayList<>(originalSteps);
        int totalSteps = steps.size();
        feedback.send("<em>Plan: " + totalSteps + " steps</em>");

        for (int i = 0; i < steps.size(); i++) {
            String step = steps.get(i);
            feedback.send("<em>Step " + (i + 1) + "/" + totalSteps + ": "
                            + HtmlEscape.escape(step) + "</em>");

            // Snapshot routed contract class before this step
            final Class<?> preStepContract = routedContractClass();

            // Build fresh context from current scene state
            AgentContext agentContext = buildAgentContext();
            ContractProfile freshProfile = agentContext.contractProfile();
            final ViewContract capturedContract = activeContract();
            final ActionGate capturedGate = gate;

            AgentResult stepResult = agentService.handlePrompt(step, freshProfile, structure);

            // Check if scene changed during (potentially slow) LLM call
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
                    // Already on the target contract — no navigation needed
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
                        // Verify our navigation landed, not a concurrent user action
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
                    // Splice nested steps into the plan after the current position
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
            feedback.send("Plan interrupted: scene did not settle in time.");
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
