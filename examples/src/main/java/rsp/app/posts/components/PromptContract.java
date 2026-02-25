package rsp.app.posts.components;

import rsp.app.posts.services.AgentService;
import rsp.app.posts.services.IntentDispatcher;
import rsp.app.posts.services.PromptService;
import rsp.component.ComponentContext;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.agent.AgentIntent;
import rsp.compositions.agent.IntentGate;
import rsp.compositions.composition.Category;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.ContractMetadata;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.ListViewContract;
import rsp.compositions.contract.NavigationEntry;
import rsp.compositions.contract.ViewContract;
import rsp.page.QualifiedSessionId;

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

    private final List<NavigationEntry> navigationEntries;

    private AgentIntent pendingConfirm;
    private Set<String> lastKnownSelection = Set.of();

    public PromptContract(Lookup lookup, PromptService promptService,
                          AgentService agentService, IntentDispatcher dispatcher,
                          IntentGate gate, Category categories) {
        super(lookup);
        this.promptService = Objects.requireNonNull(promptService);
        this.agentService = Objects.requireNonNull(agentService);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.gate = Objects.requireNonNull(gate);

        // Build navigation entries from compositions + categories (available at construction time)
        List<Composition> compositions = lookup.get(ContextKeys.APP_COMPOSITIONS);
        this.navigationEntries = buildNavigationEntries(compositions, categories);

        QualifiedSessionId sessionId = lookup.get(QualifiedSessionId.class);
        this.scopeKey = sessionId != null ? sessionId.sessionId() : "unknown-session";

        // Initialize from service history (survives contract recreation)
        for (PromptService.Message msg : promptService.getMessageHistory(scopeKey)) {
            messages.add(new Message(msg.text(), msg.fromUser()));
        }

        // Track selection changes from list views
        subscribe(ListViewContract.SELECTION_CHANGED, (_, selectedItems) -> {
            lastKnownSelection = selectedItems.ids();
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
        String trimmed = text.trim().toLowerCase();

        // Handle pending confirmation
        if (pendingConfirm != null) {
            if (trimmed.equals("yes") || trimmed.equals("y")) {
                AgentIntent confirmed = pendingConfirm;
                pendingConfirm = null;
                dispatcher.dispatch(confirmed, lookup, gate,
                        reply -> promptService.sendReply(scopeKey, reply),
                        intent -> pendingConfirm = intent);
                return;
            }
            if (trimmed.equals("no") || trimmed.equals("n")) {
                pendingConfirm = null;
                promptService.sendReply(scopeKey, "Cancelled.");
                return;
            }
        }

        // Parse user prompt into intent
        AgentIntent intent = agentService.handlePrompt(text, navigationEntries);

        if (intent == null) {
            promptService.sendReply(scopeKey, "I don't understand. Try: show posts, page 3, select all, edit selected.");
            return;
        }

        // Enrich "edit" intent with current selection if no explicit ID
        if ("edit".equals(intent.action()) && intent.params().get("id") == null
                && !lastKnownSelection.isEmpty()) {
            String firstId = lastKnownSelection.iterator().next();
            intent = new AgentIntent("edit", Map.of("id", firstId), intent.targetContract());
        }

        dispatcher.dispatch(intent, lookup, gate,
                reply -> promptService.sendReply(scopeKey, reply),
                pending -> pendingConfirm = pending);
    }

    @Override
    public String title() {
        return "Prompt";
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        return context.with(PromptContextKeys.PROMPT_MESSAGES, List.copyOf(messages));
    }

    private static List<NavigationEntry> buildNavigationEntries(List<Composition> compositions, Category categories) {
        if (compositions == null || categories == null) {
            return List.of();
        }
        final Map<String, NavigationEntry> uniqueByCategory = new LinkedHashMap<>();
        for (Composition comp : compositions) {
            for (Class<? extends ViewContract> contractClass : comp.contracts().contractClasses()) {
                Optional<String> routeOpt = comp.router().findRoutePattern(contractClass);
                if (routeOpt.isPresent() && !routeOpt.get().contains(":") && categories.contains(contractClass)) {
                    ContractMetadata metadata = categories.metadataFor(contractClass);
                    String categoryKey = metadata.categoryKey();
                    if (!uniqueByCategory.containsKey(categoryKey)) {
                        uniqueByCategory.put(categoryKey,
                                new NavigationEntry(categoryKey, metadata.navigationLabel(), contractClass, routeOpt.get()));
                    }
                }
            }
        }
        return List.copyOf(uniqueByCategory.values());
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
