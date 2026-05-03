package rsp.app.posts.components;

import rsp.app.posts.services.PromptService;
import rsp.component.ComponentContext;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.contract.ContractAction;
import rsp.compositions.agent.AgentActionFilter;
import rsp.compositions.agent.AgentContext;
import rsp.compositions.contract.ContractActionPayload;
import rsp.compositions.agent.ActionGate;
import rsp.compositions.agent.AgentService;
import rsp.compositions.agent.AgentService.AgentResult;
import rsp.compositions.agent.AgentSession;
import rsp.compositions.agent.AgentSpawner;
import rsp.compositions.agent.ContractProfile;
import rsp.compositions.agent.ControlMode;
import rsp.compositions.agent.DelegationApprovalContract;
import rsp.compositions.agent.ActionDispatcher;
import rsp.compositions.agent.ActionDispatcher.DispatchResult;
import rsp.compositions.agent.PolicyActionFilter;
import rsp.compositions.agent.PolicyGate;
import rsp.compositions.agent.SpawnRequest;
import rsp.compositions.agent.SpawnResult;
import rsp.compositions.authorization.AccessDecision;
import rsp.compositions.authorization.AttributeKeys;
import rsp.compositions.authorization.Attributes;
import rsp.compositions.authorization.Authorization;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ActionBindings;
import rsp.compositions.contract.Capabilities;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.EventKeys;
import rsp.compositions.contract.Scene;
import rsp.compositions.contract.ViewContract;
import rsp.page.QualifiedSessionId;
import rsp.util.html.HtmlEscape;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PromptContract extends ViewContract {
    private final System.Logger logger = System.getLogger(getClass().getName());

    public record Message(long id, String text, boolean fromUser) {}

    public static final EventKey.SimpleKey<String> SEND_PROMPT =
            new EventKey.SimpleKey<>("prompt.send", String.class);

    public static final EventKey.SimpleKey<Message> NEW_MESSAGE =
            new EventKey.SimpleKey<>("prompt.newMessage", Message.class);

    public static final EventKey.SimpleKey<Message> UPDATE_MESSAGE =
            new EventKey.SimpleKey<>("prompt.updateMessage", Message.class);

    private record PendingAction(ContractAction action, ContractActionPayload payload) {}

    private Runnable serviceUnsubscribe;
    private final String scopeKey;
    private final PromptService promptService;
    private final AgentService agentService;
    private final ActionDispatcher dispatcher;
    private final AgentSpawner spawner;
    private final Authorization authorization;
    private final StructureNode structure;

    private ActionGate gate;
    private AgentActionFilter actionFilter;
    private AgentSession agentSession;
    private AgentResult queuedResult;

    private volatile Scene currentScene;
    private String activeCategory = "";
    private PendingAction pendingConfirm;

    // Plan execution: completed by enrichContext() with the settled scene
    private volatile CompletableFuture<Scene> sceneSettleFuture;

    public PromptContract(Lookup lookup, PromptService promptService,
                          AgentService agentService, ActionDispatcher dispatcher,
                          Authorization authorization, AgentSpawner spawner,
                          StructureNode structure) {
        super(lookup);
        this.promptService = Objects.requireNonNull(promptService);
        this.agentService = Objects.requireNonNull(agentService);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.spawner = Objects.requireNonNull(spawner);
        this.authorization = Objects.requireNonNull(authorization);
        this.structure = structure;

        QualifiedSessionId sessionId = lookup.get(QualifiedSessionId.class);
        this.scopeKey = sessionId != null ? sessionId.sessionId() : "unknown-session";

        // Defer spawn until the policy denies an action-bearing result.
        // Pre-spawn: chat/discover are auto-allowed by the policy; everything
        // else escalates to spawner.spawn(...) lazily in evaluateAndExecute.
        this.agentSession = null;
        this.gate = (action, payload, lkp) -> new rsp.compositions.agent.GateResult.Block("No active session");
        this.actionFilter = new PolicyActionFilter(authorization);

        onCapability(Capabilities.ACTIVE_CATEGORY, category -> {
            logger.log(System.Logger.Level.DEBUG,
                () -> String.format("PromptContract@%x ACTIVE_CATEGORY = '%s' [scope=%s]",
                                    System.identityHashCode(this), category, scopeKey));
            this.activeCategory = category;
        });

        subscribe(SEND_PROMPT, (eventName, text) -> {
            logger.log(System.Logger.Level.DEBUG,
                () -> String.format("PromptContract@%x SEND_PROMPT received text='%s' [scope=%s]",
                                    System.identityHashCode(this), abbreviate(text), scopeKey));
            promptService.sendPrompt(scopeKey, text);
            handleUserInput(text);
        });

        // Listen for approval decisions from delegation dialog
        subscribe(DelegationApprovalContract.APPROVAL_DECIDED, (eventName, approved) -> {
            AgentResult queued = this.queuedResult;
            this.queuedResult = null;
            if (!approved) {
                promptService.sendReply(scopeKey, "Agent delegation denied.");
                return;
            }
            SpawnResult retry = this.spawner.spawn(
                    new SpawnRequest(AgentContext.Scope.APP, ControlMode.ASSIST, null), lookup);
            if (retry instanceof SpawnResult.Approved a) {
                installSession(a.session());
                promptService.sendReply(scopeKey, "Agent access approved.");
                if (queued != null) {
                    // Run on a virtual thread — executePlan/awaitSceneSettle would
                    // otherwise block the event loop and starve the very enrichContext
                    // that signals scene settlement.
                    final ViewContract capturedContract = activeContract();
                    Thread.startVirtualThread(() -> {
                        try {
                            executeResult(queued, capturedContract);
                        } catch (Throwable t) {
                            logger.log(System.Logger.Level.ERROR,
                                    "Post-approval execution failed", t);
                            promptService.sendReply(scopeKey,
                                    "Internal error: " + t.getClass().getSimpleName());
                        }
                    });
                }
            } else {
                promptService.sendReply(scopeKey, "Agent session could not be established.");
            }
        });

        serviceUnsubscribe = promptService.subscribe(scopeKey, message -> {
            Message msg = new Message(message.id(), message.text(), message.fromUser());
            logger.log(System.Logger.Level.DEBUG,
                () -> String.format("PromptContract@%x bridge: PromptService -> lookup@%x [%s id=%d fromUser=%s text='%s' scope=%s]",
                                    System.identityHashCode(this), System.identityHashCode(lookup),
                                    message.update() ? "UPDATE" : "NEW",
                                    msg.id(), msg.fromUser(), abbreviate(msg.text()), scopeKey));
            if (message.update()) {
                lookup.publish(UPDATE_MESSAGE, msg);
            } else {
                lookup.publish(NEW_MESSAGE, msg);
            }
        });
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("PromptContract@%x created [scope=%s, lookup@%x]",
                                System.identityHashCode(this), scopeKey, System.identityHashCode(lookup)));
    }

    private void handleUserInput(String text) {
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("PromptContract@%x handleUserInput text='%s' [queuedResult=%s, pendingConfirm=%s]",
                                System.identityHashCode(this), abbreviate(text),
                                queuedResult != null, pendingConfirm != null));
        if (queuedResult != null) {
            promptService.sendReply(scopeKey, "Still awaiting your approval for the previous request...");
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
                promptService.sendReply(scopeKey, "Cancelled.");
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
        promptService.sendReply(scopeKey, "<em>Thinking...</em>");
        Thread.startVirtualThread(() -> {
            logger.log(System.Logger.Level.DEBUG,
                () -> String.format("PromptContract@%x LLM virtual thread START [scope=%s]",
                                    System.identityHashCode(this), scopeKey));
            try {
                AgentResult agentResult = agentService.handlePrompt(text, profile, structure,
                        partial -> {
                            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                            promptService.updateLastReply(scopeKey,
                                    "<em>Thinking... (" + elapsed + "s)</em>");
                        });
                logger.log(System.Logger.Level.DEBUG,
                    () -> String.format("PromptContract@%x LLM result received: %s [scope=%s, elapsedMs=%d]",
                                        System.identityHashCode(this),
                                        agentResult.getClass().getSimpleName(), scopeKey,
                                        System.currentTimeMillis() - startTime));
                evaluateAndExecute(agentResult, capturedContract);
            } catch (Throwable t) {
                logger.log(System.Logger.Level.ERROR, "Prompt processing failed", t);
                promptService.sendReply(scopeKey,
                        "Internal error: " + t.getClass().getSimpleName()
                                + (t.getMessage() != null ? " - " + t.getMessage() : ""));
            } finally {
                logger.log(System.Logger.Level.DEBUG,
                    () -> String.format("PromptContract@%x LLM virtual thread END [scope=%s]",
                                        System.identityHashCode(this), scopeKey));
            }
        });
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
            () -> String.format("PromptContract@%x evaluateAndExecute: %s -> %s [scope=%s, hasSession=%s]",
                                System.identityHashCode(this), result.getClass().getSimpleName(),
                                decision.getClass().getSimpleName(), scopeKey,
                                agentSession != null && agentSession.isValid()));

        if (decision instanceof AccessDecision.Allow) {
            executeResult(result, capturedContract);
            return;
        }
        if (agentSession != null && agentSession.isValid()) {
            // Grant is present and policy still denies — real refusal.
            String reason = (decision instanceof AccessDecision.Deny d) ? d.reason() : "denied";
            promptService.sendReply(scopeKey, "Action not permitted: " + reason);
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
                promptService.sendReply(scopeKey, "This requires your approval...");
            }
            case SpawnResult.Denied d ->
                promptService.sendReply(scopeKey, "Agent session denied: " + d.reason());
        }
    }

    private void executeResult(AgentResult result, ViewContract capturedContract) {
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("PromptContract@%x executeResult: %s [scope=%s]",
                                System.identityHashCode(this),
                                result.getClass().getSimpleName(), scopeKey));
        final ActionGate capturedGate = gate;
        switch (result) {
            case AgentResult.TextReply reply ->
                promptService.sendReply(scopeKey, HtmlEscape.escape(reply.message()));
            case AgentResult.NavigateResult nav -> {
                dispatcher.dispatchNavigate(nav.targetContract(), lookup);
                promptService.sendReply(scopeKey, "Navigating...");
            }
            case AgentResult.ActionResult actionResult -> {
                ContractAction action = actionResult.action();
                DispatchResult dr = dispatcher.dispatch(
                    action, actionResult.payload(), capturedContract, lookup, capturedGate);
                handleDispatchResult(dr);
            }
            case AgentResult.PlanResult plan -> {
                if (!plan.summary().isBlank()) {
                    promptService.sendReply(scopeKey, HtmlEscape.escape(plan.summary()));
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
                promptService.sendReply(scopeKey, d.action().description());
            case DispatchResult.Blocked b ->
                promptService.sendReply(scopeKey, b.reason());
            case DispatchResult.AwaitingConfirmation c -> {
                promptService.sendReply(scopeKey, c.question());
                pendingConfirm = new PendingAction(c.action(), c.payload());
            }
            case DispatchResult.PayloadError pe ->
                promptService.sendReply(scopeKey, "Invalid payload for '" + HtmlEscape.escape(pe.action())
                    + "': " + HtmlEscape.escape(pe.message()));
        }
    }

    private static final int MAX_PLAN_STEPS = 20;

    private void executePlan(List<String> originalSteps) {
        List<String> steps = new ArrayList<>(originalSteps);
        int totalSteps = steps.size();
        promptService.sendReply(scopeKey,
                "<em>Plan: " + totalSteps + " steps</em>");

        for (int i = 0; i < steps.size(); i++) {
            String step = steps.get(i);
            promptService.sendReply(scopeKey,
                    "<em>Step " + (i + 1) + "/" + totalSteps + ": "
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
                promptService.sendReply(scopeKey,
                        "Plan interrupted: scene changed during step execution.");
                return;
            }

            switch (stepResult) {
                case AgentResult.TextReply reply -> {
                    promptService.sendReply(scopeKey, HtmlEscape.escape(reply.message()));
                    return;
                }
                case AgentResult.NavigateResult nav -> {
                    // Already on the target contract — no navigation needed
                    if (currentScene != null && currentScene.routedContract() != null
                            && nav.targetContract().isInstance(currentScene.routedContract())) {
                        promptService.sendReply(scopeKey,
                                "Already on " + nav.targetContract().getSimpleName());
                    } else {
                        sceneSettleFuture = new CompletableFuture<>();
                        dispatcher.dispatchNavigate(nav.targetContract(), lookup);
                        promptService.sendReply(scopeKey, "Navigating...");

                        Scene settled = awaitSceneSettle();
                        if (settled == null) {
                            return;
                        }
                        // Verify our navigation landed, not a concurrent user action
                        if (settled.routedContract() == null
                                || !nav.targetContract().isInstance(settled.routedContract())) {
                            promptService.sendReply(scopeKey,
                                    "Plan interrupted: unexpected navigation.");
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
                            promptService.sendReply(scopeKey, d.action().description());
                            d.processed().join();
                        }
                        case DispatchResult.Blocked b -> {
                            promptService.sendReply(scopeKey, "Step blocked: " + b.reason());
                            return;
                        }
                        case DispatchResult.AwaitingConfirmation c -> {
                            promptService.sendReply(scopeKey,
                                    "Plan paused: action requires confirmation. " + c.question());
                            return;
                        }
                        case DispatchResult.PayloadError pe -> {
                            promptService.sendReply(scopeKey,
                                    "Step failed: " + HtmlEscape.escape(pe.message()));
                            return;
                        }
                    }
                }
                case AgentResult.PlanResult nested -> {
                    // Splice nested steps into the plan after the current position
                    steps.addAll(i + 1, nested.steps());
                    totalSteps = steps.size();
                    if (totalSteps > MAX_PLAN_STEPS) {
                        promptService.sendReply(scopeKey,
                                "Plan too large (" + totalSteps + " steps); aborting.");
                        return;
                    }
                }
            }
        }

        promptService.sendReply(scopeKey,
                "<em>Plan complete (" + totalSteps + " steps executed)</em>");
    }

    private Scene awaitSceneSettle() {
        CompletableFuture<Scene> future = sceneSettleFuture;
        if (future == null) return currentScene;
        try {
            Scene settled = future.get(5, TimeUnit.SECONDS);
            sceneSettleFuture = null;
            return settled;
        } catch (TimeoutException e) {
            promptService.sendReply(scopeKey,
                    "Plan interrupted: scene did not settle in time.");
            return null;
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private String buildHelpMessage(ContractProfile profile) {
        StringBuilder help = new StringBuilder("I don't understand..");
        help.append("<ul>");
        help.append("<li><b>show</b> &lt;section&gt; &mdash; navigate to a section</li>");
        for (ContractAction action : profile.actions()) {
            help.append("<li><b>").append(action.action()).append("</b> &mdash; ")
                .append(action.description()).append("</li>");
        }
        help.append("</ul>");
        if (structure != null) {
            String sections = structure.agentDescription();
            if (!sections.isEmpty()) {
                help.append("<pre>").append(sections).append("</pre>");
            }
        }
        return help.toString();
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

    @Override
    public String title() {
        return "Prompt";
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        this.currentScene = context.get(ContextKeys.SCENE);
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("PromptContract@%x enrichContext [scope=%s, sceneRouted=%s, activeCategory='%s']",
                                System.identityHashCode(this), scopeKey,
                                currentScene == null || currentScene.routedContract() == null
                                    ? "null"
                                    : currentScene.routedContract().getClass().getSimpleName(),
                                activeCategory));
        // Signal waiting plan executor with the settled scene
        CompletableFuture<Scene> future = this.sceneSettleFuture;
        if (future != null && !future.isDone()) {
            future.complete(currentScene);
        }
        return context.with(PromptContextKeys.PROMPT_SERVICE, promptService)
                      .with(PromptContextKeys.SCOPE_KEY, scopeKey)
                      .with(PromptContextKeys.ACTIVE_CATEGORY, activeCategory);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        boolean hadBridge = serviceUnsubscribe != null;
        if (serviceUnsubscribe != null) {
            serviceUnsubscribe.run();
            serviceUnsubscribe = null;
        }
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("PromptContract@%x destroyed [scope=%s, bridgeUnsubscribed=%s]",
                                System.identityHashCode(this), scopeKey, hadBridge));
    }

    private static String abbreviate(String s) {
        if (s == null) return "null";
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= 60 ? oneLine : oneLine.substring(0, 57) + "...";
    }
}
