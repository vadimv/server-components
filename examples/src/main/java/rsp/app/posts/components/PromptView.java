package rsp.app.posts.components;

import rsp.app.posts.services.PromptService;
import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.util.html.HtmlEscape;
import rsp.util.json.JsonDataType;

import java.util.ArrayList;
import java.util.List;

import static rsp.dsl.Html.*;

public class PromptView extends Component<PromptView.PromptViewState> {
    private final System.Logger logger = System.getLogger(getClass().getName());

    public record PromptViewState(List<PromptContract.Message> messages, String activeCategory) {
        public PromptViewState(List<PromptContract.Message> messages) {
            this(messages, "");
        }

        public PromptViewState withMessage(PromptContract.Message message) {
            // Idempotent: skip if message already present (by ID)
            if (messages.stream().anyMatch(m -> m.id() == message.id())) {
                return this;
            }
            List<PromptContract.Message> updated = new ArrayList<>(messages);
            updated.add(message);
            return new PromptViewState(List.copyOf(updated), activeCategory);
        }

        public PromptViewState withLastSystemMessageUpdated(String text) {
            List<PromptContract.Message> updated = new ArrayList<>(messages);
            for (int i = updated.size() - 1; i >= 0; i--) {
                if (!updated.get(i).fromUser()) {
                    updated.set(i, new PromptContract.Message(updated.get(i).id(), text, false));
                    break;
                }
            }
            return new PromptViewState(List.copyOf(updated), activeCategory);
        }
    }

    private Lookup lookup;
    private Lookup.Registration eventSubscription;
    private Lookup.Registration updateSubscription;

    @Override
    public ComponentStateSupplier<PromptViewState> initStateSupplier() {
        return (_, context) -> {
            PromptService promptService = context.get(PromptContextKeys.PROMPT_SERVICE);
            String scopeKey = context.get(PromptContextKeys.SCOPE_KEY);
            String category = context.get(PromptContextKeys.ACTIVE_CATEGORY);
            String activeCategory = category != null ? category : "";
            if (promptService != null && scopeKey != null) {
                List<PromptContract.Message> history = promptService.getMessageHistory(scopeKey)
                    .stream()
                    .map(m -> new PromptContract.Message(m.id(), m.text(), m.fromUser()))
                    .toList();
                return new PromptViewState(history, activeCategory);
            }
            return new PromptViewState(List.of(), activeCategory);
        };
    }

    @Override
    public ComponentSegment<PromptViewState> createComponentSegment(final QualifiedSessionId sessionId,
                                                                    final TreePositionPath componentPath,
                                                                    final TreeBuilderFactory treeBuilderFactory,
                                                                    final ComponentContext componentContext,
                                                                    final CommandsEnqueue commandsEnqueue) {
        this.lookup = createLookup(componentContext, commandsEnqueue);
        return super.createComponentSegment(sessionId, componentPath, treeBuilderFactory, componentContext, commandsEnqueue);
    }

    private Lookup createLookup(ComponentContext context, CommandsEnqueue commandsEnqueue) {
        Subscriber subscriber = context.get(Subscriber.class);
        if (subscriber == null) {
            subscriber = NoOpSubscriber.INSTANCE;
        }
        return new ContextLookup(context, commandsEnqueue, subscriber);
    }

    @Override
    public ComponentView<PromptViewState> componentView() {
        return stateUpdate -> state -> div(attr("class", "prompt-panel"),
                div(attr("class", "prompt-header"), text("Prompt")),
                div(attr("class", "prompt-messages"),
                        of(state.messages().reversed().stream().map(msg ->
                                div(attr("class", msg.fromUser() ? "prompt-message user" : "prompt-message system"),
                                        attr("innerHTML",
                                             msg.fromUser() ? HtmlEscape.escape(msg.text())
                                                            : msg.text(),
                                             true))
                        ))
                ),
                form(attr("class", "prompt-input-form"),
                        textarea(attr("name", "prompt"),
                                attr("class", "prompt-input"),
                                attr("placeholder", "Type a command..."),
                                attr("autocomplete", "off"),
                                attr("rows", "1"),
                                attr("onkeydown",
                                     "if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();this.form.requestSubmit();}",
                                     false)),
                        div(attr("class", "prompt-footer"),
                                span(attr("class", "prompt-category"), text(state.activeCategory())),
                                button(attr("type", "submit"),
                                        attr("class", "prompt-send"),
                                        attr("aria-label", "Send"),
                                        text("\u2191"))),
                        on("submit", true, ctx -> {
                            JsonDataType.Object eventObj = ctx.eventObject();
                            JsonDataType promptValue = eventObj.value("prompt");
                            if (promptValue != null) {
                                String text = promptValue.toString().replace("\"", "");
                                if (!text.isEmpty()) {
                                    if (stateUpdate != null) {
                                        stateUpdate.applyStateTransformation(s ->
                                                s.withMessage(new PromptContract.Message(-1, text, true)));
                                    }
                                    lookup.publish(PromptContract.SEND_PROMPT, text);
                                }
                            }
                            ctx.evalJs("document.querySelector('.prompt-input').value = ''");
                        })
                )
        );
    }

    @Override
    public void onMounted(ComponentCompositeKey componentId, PromptViewState state, StateUpdate<PromptViewState> stateUpdate) {
        // History already loaded synchronously in initStateSupplier.
        // Subscribe for incremental updates only — ID dedup in withMessage prevents duplicates.
        eventSubscription = lookup.subscribe(PromptContract.NEW_MESSAGE, (eventName, message) -> {
            logger.log(System.Logger.Level.TRACE, () -> "New message notified: " + message);
            stateUpdate.applyStateTransformation(s -> s.withMessage(message));
        });
        updateSubscription = lookup.subscribe(PromptContract.UPDATE_MESSAGE, (eventName, message) -> {
            logger.log(System.Logger.Level.TRACE, () -> "Update message notified: " + message);
            stateUpdate.applyStateTransformation(s -> s.withLastSystemMessageUpdated(message.text()));
        });
    }

    @Override
    public void onUnmounted(ComponentCompositeKey componentId, PromptViewState state) {
        if (eventSubscription != null) {
            eventSubscription.unsubscribe();
            eventSubscription = null;
        }
        if (updateSubscription != null) {
            updateSubscription.unsubscribe();
            updateSubscription = null;
        }
    }

    private static final class NoOpSubscriber implements Subscriber {
        static final NoOpSubscriber INSTANCE = new NoOpSubscriber();

        @Override
        public void addWindowEventHandler(String eventType, java.util.function.Consumer<rsp.page.EventContext> eventHandler,
                                          boolean preventDefault, rsp.dom.DomEventEntry.Modifier modifier) {}

        @Override
        public void addComponentEventHandler(String eventType, java.util.function.Consumer<ComponentEventEntry.EventContext> eventHandler,
                                             boolean preventDefault) {}

        @Override
        public void removeComponentEventHandler(String eventType) {}
    }
}
