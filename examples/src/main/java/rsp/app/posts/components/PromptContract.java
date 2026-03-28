package rsp.app.posts.components;

import rsp.app.posts.services.PromptService;
import rsp.component.ComponentContext;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.agent.AgentAction;
import rsp.compositions.agent.AgentActionFilter;
import rsp.compositions.agent.AgentContext;
import rsp.compositions.agent.AgentPayload;
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
import rsp.compositions.authorization.Authorization;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ActionBindings;
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

    public record Message(String text, boolean fromUser) {}

    public static final EventKey.SimpleKey<String> SEND_PROMPT =
            new EventKey.SimpleKey<>("prompt.send", String.class);

    public static final EventKey.SimpleKey<Message> NEW_MESSAGE =
            new EventKey.SimpleKey<>("prompt.newMessage", Message.class);

    public static final EventKey.SimpleKey<Message> UPDATE_MESSAGE =
            new EventKey.SimpleKey<>("prompt.updateMessage", Message.class);

    private record PendingAction(AgentAction action, AgentPayload payload) {}

    private final List<Message> messages = new ArrayList<>();
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
    private SpawnRequest pendingSpawnRequest;
    private String pendingTicketId;
    private String queuedPrompt;

    private volatile Scene currentScene;
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

        // Spawn agent session via centralized spawner
        this.pendingSpawnRequest = new SpawnRequest(AgentContext.Scope.APP, ControlMode.ASSIST, null);
        SpawnResult spawnResult = spawner.spawn(pendingSpawnRequest, lookup);
        this.agentSession = switch (spawnResult) {
            case SpawnResult.Approved approved -> approved.session();
            case SpawnResult.Denied denied -> {
                logger.log(System.Logger.Level.WARNING,
                    () -> "Agent session denied: " + denied.reason());
                yield null;
            }
            case SpawnResult.RequiresApproval pending -> {
                logger.log(System.Logger.Level.INFO,
                    () -> "Agent session pending approval: " + pending.reason());
                this.pendingTicketId = pending.ticketId();
                yield null;
            }
        };

        // Derive gate and filter from the session's grant via unified authorization
        if (agentSession != null) {
            Authorization agentAuth = authorization.delegated(agentSession.grant());
            this.gate = new PolicyGate(agentAuth);
            this.actionFilter = new PolicyActionFilter(agentAuth);
        } else {
            this.gate = (action, payload, lkp) -> new rsp.compositions.agent.GateResult.Block("No active session");
            this.actionFilter = (actions, ctx) -> List.of();
        }

        // Initialize from service history (survives contract recreation)
        for (PromptService.Message msg : promptService.getMessageHistory(scopeKey)) {
            messages.add(new Message(msg.text(), msg.fromUser()));
        }

        subscribe(SEND_PROMPT, (eventName, text) -> {
            messages.add(new Message(text, true));
            promptService.sendPrompt(scopeKey, text);
            handleUserInput(text);
        });

        // Listen for approval decisions from delegation dialog
        subscribe(DelegationApprovalContract.APPROVAL_DECIDED, (eventName, approved) -> {
            if (approved) {
                SpawnResult retryResult = this.spawner.spawn(pendingSpawnRequest, lookup);
                if (retryResult instanceof SpawnResult.Approved a) {
                    this.agentSession = a.session();
                    Authorization agentAuth = this.authorization.delegated(agentSession.grant());
                    this.gate = new PolicyGate(agentAuth);
                    this.actionFilter = new PolicyActionFilter(agentAuth);
                    this.pendingTicketId = null;
                    promptService.sendReply(scopeKey, "Agent access approved.");
                    if (queuedPrompt != null) {
                        String prompt = queuedPrompt;
                        queuedPrompt = null;
                        handleUserInput(prompt);
                    }
                } else {
                    promptService.sendReply(scopeKey, "Agent session could not be established.");
                }
            } else {
                this.pendingTicketId = null;
                promptService.sendReply(scopeKey, "Agent delegation denied.");
            }
        });

        serviceUnsubscribe = promptService.subscribe(scopeKey, message -> {
            Message msg = new Message(message.text(), message.fromUser());
            if (message.update()) {
                // Replace last system message in local list
                for (int i = messages.size() - 1; i >= 0; i--) {
                    if (!messages.get(i).fromUser()) {
                        messages.set(i, msg);
                        break;
                    }
                }
                lookup.publish(UPDATE_MESSAGE, msg);
            } else {
                messages.add(msg);
                lookup.publish(NEW_MESSAGE, msg);
            }
        });
        logger.log(System.Logger.Level.TRACE, () -> "PromptContract created");
    }

    private void handleUserInput(String text) {
        if (agentSession == null || !agentSession.isValid()) {
            if (pendingTicketId != null) {
                this.queuedPrompt = text;
                lookup.publish(EventKeys.SHOW, new ActionBindings.ShowPayload(
                        DelegationApprovalContract.class,
                        Map.of("scope", pendingSpawnRequest.scope().name(),
                               "controlMode", pendingSpawnRequest.controlMode().name(),
                               "reason", pendingSpawnRequest.purpose() != null
                                       ? pendingSpawnRequest.purpose() : "")));
                promptService.sendReply(scopeKey, "Awaiting your approval for agent access...");
                return;
            }
            promptService.sendReply(scopeKey, "Agent session is not active.");
            return;
        }

        String trimmed = text.trim().toLowerCase();

        // Handle pending confirmation
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

        // Parse user prompt via AgentService off the event loop
        AgentContext agentContext = buildAgentContext();
        ContractProfile profile = agentContext.contractProfile();

        // Capture mutable state before spawning the virtual thread
        // to avoid races with scene rebuilds on the event loop
        final ViewContract capturedContract = activeContract();
        final ActionGate capturedGate = gate;

        final long startTime = System.currentTimeMillis();
        promptService.sendReply(scopeKey, "<em>Thinking...</em>");
        Thread.startVirtualThread(() -> {
            AgentResult agentResult = agentService.handlePrompt(text, profile, structure,
                    partial -> {
                        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                        promptService.updateLastReply(scopeKey,
                                "<em>Thinking... (" + elapsed + "s)</em>");
                    });
            switch (agentResult) {
                case AgentResult.TextReply reply ->
                    promptService.sendReply(scopeKey, HtmlEscape.escape(reply.message()));
                case AgentResult.NavigateResult nav -> {
                    dispatcher.dispatchNavigate(nav.targetContract(), lookup);
                    promptService.sendReply(scopeKey, "Navigating...");
                }
                case AgentResult.ActionResult actionResult -> {
                    AgentAction action = actionResult.action();
                    DispatchResult result = dispatcher.dispatch(
                        action, actionResult.payload(), capturedContract, lookup, capturedGate);
                    handleDispatchResult(result);
                }
                case AgentResult.PlanResult plan -> {
                    if (!plan.summary().isBlank()) {
                        promptService.sendReply(scopeKey, HtmlEscape.escape(plan.summary()));
                    }
                    executePlan(plan.steps());
                }
            }
        });
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

    private void executePlan(List<String> steps) {
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
                case AgentResult.ActionResult actionResult -> {
                    DispatchResult dispatchResult = dispatcher.dispatch(
                            actionResult.action(), actionResult.payload(),
                            capturedContract, lookup, capturedGate);
                    switch (dispatchResult) {
                        case DispatchResult.Dispatched d ->
                            promptService.sendReply(scopeKey, d.action().description());
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
                case AgentResult.PlanResult _ -> {
                    promptService.sendReply(scopeKey, "Nested plan detected; aborting.");
                    return;
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
        for (AgentAction action : profile.actions()) {
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
        // Signal waiting plan executor with the settled scene
        CompletableFuture<Scene> future = this.sceneSettleFuture;
        if (future != null && !future.isDone()) {
            future.complete(currentScene);
        }
        return context.with(PromptContextKeys.PROMPT_MESSAGES, List.copyOf(messages));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceUnsubscribe != null) {
            serviceUnsubscribe.run();
            serviceUnsubscribe = null;
        }
        logger.log(System.Logger.Level.TRACE, () -> "PromptContract destroyed");
    }
}
