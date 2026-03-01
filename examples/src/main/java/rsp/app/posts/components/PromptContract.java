package rsp.app.posts.components;

import rsp.app.posts.services.PromptService;
import rsp.component.ComponentContext;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.agent.AgentActionFilter;
import rsp.compositions.agent.AgentContext;
import rsp.compositions.agent.AgentIntent;
import rsp.compositions.agent.AgentService;
import rsp.compositions.agent.AgentService.AgentResult;
import rsp.compositions.agent.AgentSession;
import rsp.compositions.agent.AgentSpawner;
import rsp.compositions.agent.ContractProfile;
import rsp.compositions.agent.ControlMode;
import rsp.compositions.agent.IntentGate;
import rsp.compositions.agent.IntentDispatcher;
import rsp.compositions.agent.IntentDispatcher.DispatchResult;
import rsp.compositions.agent.SpawnRequest;
import rsp.compositions.agent.SpawnResult;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.EventKeys;
import rsp.compositions.contract.ListViewContract;
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

    private final List<Message> messages = new ArrayList<>();
    private Runnable serviceUnsubscribe;
    private final String scopeKey;
    private final PromptService promptService;
    private final AgentService agentService;
    private final IntentDispatcher dispatcher;
    private final IntentGate gate;
    private final AgentActionFilter actionFilter;
    private final StructureNode structure;
    private final AgentSession agentSession;

    private Scene currentScene;
    private AgentIntent pendingConfirm;
    private Set<String> lastKnownSelection = Set.of();

    public PromptContract(Lookup lookup, PromptService promptService,
                          AgentService agentService, IntentDispatcher dispatcher,
                          IntentGate gate, AgentActionFilter actionFilter,
                          AgentSpawner spawner, StructureNode structure) {
        super(lookup);
        this.promptService = Objects.requireNonNull(promptService);
        this.agentService = Objects.requireNonNull(agentService);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.gate = Objects.requireNonNull(gate);
        this.actionFilter = actionFilter; // nullable = no filtering
        this.structure = structure;

        QualifiedSessionId sessionId = lookup.get(QualifiedSessionId.class);
        this.scopeKey = sessionId != null ? sessionId.sessionId() : "unknown-session";

        // Spawn agent session via centralized spawner
        SpawnResult spawnResult = spawner.spawn(
            new SpawnRequest(AgentContext.Scope.APP, ControlMode.ASSIST, null), lookup);
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
                yield null;
            }
        };

        // Initialize from service history (survives contract recreation)
        for (PromptService.Message msg : promptService.getMessageHistory(scopeKey)) {
            messages.add(new Message(msg.text(), msg.fromUser()));
        }

        // Track selection changes from list views
        subscribe(ListViewContract.SELECTION_CHANGED, (_, selectedItems) -> {
            lastKnownSelection = selectedItems.ids();
        });

        // Reset agent state on navigation changes
        subscribe(EventKeys.SET_PRIMARY, (_, contractClass) -> {
            agentService.reset();
            lastKnownSelection = Set.of();
        });

        subscribe(SEND_PROMPT, (eventName, text) -> {
            messages.add(new Message(text, true));
            promptService.sendPrompt(scopeKey, text);
            handleUserInput(text);
        });

        serviceUnsubscribe = promptService.subscribe(scopeKey, message -> {
            Message msg = new Message(message.text(), message.fromUser());
            messages.add(msg);
            lookup.publish(NEW_MESSAGE, msg);
        });
        logger.log(System.Logger.Level.TRACE, () -> "PromptContract created");
    }

    private void handleUserInput(String text) {
        if (agentSession == null || !agentSession.isValid()) {
            promptService.sendReply(scopeKey, "Agent session is not active.");
            return;
        }

        String trimmed = text.trim().toLowerCase();

        // Handle pending confirmation
        if (pendingConfirm != null) {
            if (trimmed.equals("yes") || trimmed.equals("y")) {
                AgentIntent confirmed = pendingConfirm;
                pendingConfirm = null;
                ViewContract activeContract = activeContract();
                DispatchResult result = dispatcher.dispatchDirect(confirmed, activeContract, lookup);
                handleDispatchResult(result);
                return;
            }
            if (trimmed.equals("no") || trimmed.equals("n")) {
                pendingConfirm = null;
                promptService.sendReply(scopeKey, "Cancelled.");
                return;
            }
        }

        // Parse user prompt via framework AgentService (with scoped, filtered profile)
        AgentContext agentContext = buildAgentContext();
        ContractProfile profile = agentContext.contractProfile();
        AgentResult agentResult = agentService.handlePrompt(text, profile, structure);

        switch (agentResult) {
            case AgentResult.TextReply reply -> {
                if (reply.message().startsWith("I don't understand")) {
                    promptService.sendReply(scopeKey, buildHelpMessage(profile));
                } else {
                    promptService.sendReply(scopeKey, HtmlEscape.escape(reply.message()));
                }
            }
            case AgentResult.IntentResult intentResult -> {
                AgentIntent intent = intentResult.intent();

                // Enrich "edit" intent with current selection if no explicit payload
                if ("edit".equals(intent.action()) && intent.params().get("payload") == null
                        && !lastKnownSelection.isEmpty()) {
                    String firstId = lastKnownSelection.iterator().next();
                    intent = new AgentIntent("edit", Map.of("payload", firstId), intent.targetContract());
                }

                ViewContract activeContract = activeContract();
                DispatchResult result = dispatcher.dispatch(intent, activeContract, lookup, gate);
                handleDispatchResult(result);
            }
        }
    }

    private void handleDispatchResult(DispatchResult result) {
        switch (result) {
            case DispatchResult.Dispatched d -> {
                String reply = replyForIntent(d.intent());
                promptService.sendReply(scopeKey, reply);
            }
            case DispatchResult.Blocked b ->
                promptService.sendReply(scopeKey, b.reason());
            case DispatchResult.AwaitingConfirmation c -> {
                promptService.sendReply(scopeKey, c.question());
                pendingConfirm = c.intent();
            }
            case DispatchResult.UnknownAction u ->
                promptService.sendReply(scopeKey, "Unknown action: " + HtmlEscape.escape(u.action()));
        }
    }

    private String replyForIntent(AgentIntent intent) {
        return switch (intent.action()) {
            case "navigate" -> "Navigating...";
            case "page" -> "Going to page " + intent.params().get("payload") + ".";
            case "select_all" -> "Selected all items.";
            case "edit" -> "Opening editor...";
            case "create" -> "Opening create form.";
            case "delete" -> "Deleting...";
            case "save" -> "Saving...";
            default -> "Done.";
        };
    }

    private String buildHelpMessage(ContractProfile profile) {
        StringBuilder help = new StringBuilder("I don't understand..");
        help.append("<ul>");
        help.append("<li><b>show</b> &lt;section&gt; &mdash; navigate to a section</li>");
        help.append("<li><b>page</b> &lt;n&gt; &mdash; go to page number</li>");
        help.append("<li><b>select all</b> &mdash; select all items</li>");
        help.append("<li><b>edit selected</b> &mdash; edit the selected item</li>");
        help.append("<li><b>delete</b> &lt;name&gt; &mdash; delete an item by name</li>");
        help.append("<li><b>create</b> &mdash; create a new item</li>");
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
