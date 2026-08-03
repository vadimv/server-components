package rsp.compositions.agentui;

import rsp.component.ComponentView;
import rsp.component.IntentDispatcher;
import rsp.util.html.HtmlEscape;
import rsp.util.json.JsonDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static rsp.dsl.Html.*;

public class PromptView implements ComponentView<PromptView.PromptViewState, PromptView.PromptIntent> {

    public record PromptViewState(List<PromptContract.Message> messages,
                                  String activeCategory,
                                  long nextOptimisticId) {
        public PromptViewState {
            messages = messages == null ? List.of() : List.copyOf(messages);
            activeCategory = activeCategory != null ? activeCategory : "";
        }

        public PromptViewState(List<PromptContract.Message> messages) {
            this(messages, "", 0);
        }

        public PromptViewState(List<PromptContract.Message> messages, String activeCategory) {
            this(messages, activeCategory, 0);
        }

        public PromptViewState withMessage(PromptContract.Message message) {
            // Idempotent: skip if message already present (by ID)
            if (messages.stream().anyMatch(m -> m.id() == message.id())) {
                return this;
            }
            List<PromptContract.Message> updated = new ArrayList<>(messages);
            updated.add(message);
            return new PromptViewState(updated, activeCategory, nextOptimisticId);
        }

        public PromptViewState withOptimisticMessage(String text) {
            final long optimisticId = nextOptimisticId - 1;
            return withMessage(new PromptContract.Message(optimisticId, text, true))
                    .withNextOptimisticId(optimisticId);
        }

        public PromptViewState withLastSystemMessageUpdated(String text) {
            List<PromptContract.Message> updated = new ArrayList<>(messages);
            for (int i = updated.size() - 1; i >= 0; i--) {
                if (!updated.get(i).fromUser()) {
                    updated.set(i, new PromptContract.Message(updated.get(i).id(), text, false));
                    break;
                }
            }
            return new PromptViewState(updated, activeCategory, nextOptimisticId);
        }

        public PromptViewState withActiveCategory(String activeCategory) {
            final String nextCategory = activeCategory != null ? activeCategory : "";
            if (Objects.equals(this.activeCategory, nextCategory)) {
                return this;
            }
            return new PromptViewState(messages, nextCategory, nextOptimisticId);
        }

        private PromptViewState withNextOptimisticId(long nextOptimisticId) {
            if (this.nextOptimisticId == nextOptimisticId) {
                return this;
            }
            return new PromptViewState(messages, activeCategory, nextOptimisticId);
        }
    }

    public record PromptIntent(String text) {
        public PromptIntent {
            Objects.requireNonNull(text, "text");
        }
    }

    @Override
    public rsp.component.View<PromptViewState> use(IntentDispatcher<PromptIntent> intents) {
        return state -> div(attr("class", "prompt-panel"),
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
                                    intents.dispatch(new PromptIntent(text));
                                }
                            }
                            ctx.evalJs("document.querySelector('.prompt-input').value = ''");
                        })
                )
        );
    }

}
