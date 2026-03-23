package rsp.app.posts.components;

import rsp.app.posts.services.PromptService;
import rsp.component.ComponentContext;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.agent.AgentAction;
import rsp.compositions.agent.AgentActionFilter;
import rsp.compositions.agent.AgentContext;
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

public class PromptContract extends ViewContract {
    private final System.Logger logger = System.getLogger(getClass().getName());

    public record Message(String text, boolean fromUser) {}

    public static final EventKey.SimpleKey<String> SEND_PROMPT =
            new EventKey.SimpleKey<>("prompt.send", String.class);

    public static final EventKey.SimpleKey<Message> NEW_MESSAGE =
            new EventKey.SimpleKey<>("prompt.newMessage", Message.class);

    public static final EventKey.SimpleKey<Message> UPDATE_MESSAGE =
            new EventKey.SimpleKey<>("prompt.updateMessage", Message.class);

    private record PendingAction(AgentAction action, Object rawPayload) {}

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

    private Scene currentScene;
    private PendingAction pendingConfirm;

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
            this.gate = (action, rawPayload, lkp) -> new rsp.compositions.agent.GateResult.Block("No active session");
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
                    confirmed.action(), confirmed.rawPayload(), activeContract);
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

        final long startTime = System.currentTimeMillis();
        promptService.sendReply(scopeKey, "<em>Thinking...</em>");
        Thread.startVirtualThread(() -> {
            AgentResult agentResult = agentService.handlePrompt(text, profile, structure,
                    partial -> {
                        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                        promptService.updateLastReply(scopeKey,
                                "<em>Thinking... (" + elapsed + "s)</em>");
                    });
            // Dispatch result back on the event loop via publish
            switch (agentResult) {
                case AgentResult.TextReply reply ->
                    promptService.sendReply(scopeKey, HtmlEscape.escape(reply.message()));
                case AgentResult.NavigateResult nav -> {
                    dispatcher.dispatchNavigate(nav.targetContract(), lookup);
                    promptService.sendReply(scopeKey, "Navigating...");
                }
                case AgentResult.ActionResult actionResult -> {
                    AgentAction action = actionResult.action();
                    ViewContract activeContract = activeContract();
                    DispatchResult result = dispatcher.dispatch(
                        action, actionResult.rawPayload(), activeContract, lookup, gate);
                    handleDispatchResult(result);
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
                pendingConfirm = new PendingAction(c.action(), c.rawPayload());
            }
            case DispatchResult.PayloadError pe ->
                promptService.sendReply(scopeKey, "Invalid payload for '" + HtmlEscape.escape(pe.action())
                    + "': " + HtmlEscape.escape(pe.message()));
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

    @Override
    public String title() {
        return "Prompt";
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        // Capture Scene from enriched context (not available in lookup at construction time)
        this.currentScene = context.get(ContextKeys.SCENE);
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
