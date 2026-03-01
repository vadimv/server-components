package rsp.app.posts.services;

import rsp.app.posts.entities.Post;
import rsp.component.Lookup;
import rsp.compositions.agent.AgentIntent;
import rsp.compositions.agent.GateResult;
import rsp.compositions.agent.IntentGate;
import rsp.compositions.contract.EventKeys;
import rsp.compositions.contract.ListViewContract;

import rsp.util.html.HtmlEscape;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Translates allowed {@link AgentIntent}s to framework events.
 * <p>
 * The only component with publish access — all agent actions flow through here.
 * Uses callbacks for replies (not coupled to PromptService directly).
 */
public class IntentDispatcher {

    private final PostService postService;

    public IntentDispatcher(PostService postService) {
        this.postService = postService;
    }

    /**
     * Evaluate an intent through the gate and dispatch if allowed.
     *
     * @param intent           the agent's intent
     * @param lookup           the current lookup (for publishing events)
     * @param gate             the intent gate (rules)
     * @param onMessage        callback for reply messages to the user
     * @param onConfirmPending callback when confirmation is required (stores pending intent)
     */
    public void dispatch(AgentIntent intent, Lookup lookup, IntentGate gate,
                         Consumer<String> onMessage, Consumer<AgentIntent> onConfirmPending) {
        GateResult result = gate.evaluate(intent, lookup);
        switch (result) {
            case GateResult.Allow a -> publishEvent(a.intent(), lookup, onMessage);
            case GateResult.Block b -> onMessage.accept(b.reason());
            case GateResult.Confirm c -> {
                onMessage.accept(c.question());
                onConfirmPending.accept(c.intent());
            }
        }
    }

    private void publishEvent(AgentIntent intent, Lookup lookup, Consumer<String> onMessage) {
        switch (intent.action()) {
            case "navigate" -> {
                Class targetClass = intent.targetContract();
                lookup.publish(EventKeys.SET_PRIMARY, targetClass);
                onMessage.accept("Navigating...");
            }
            case "page" -> {
                int page = (Integer) intent.params().get("page");
                lookup.publish(ListViewContract.PAGE_CHANGE_REQUESTED, page);
                onMessage.accept("Going to page " + page + ".");
            }
            case "select_all" -> {
                lookup.publish(ListViewContract.SELECT_ALL_REQUESTED);
                onMessage.accept("Selected all items.");
            }
            case "edit" -> {
                String id = (String) intent.params().get("id");
                if (id != null) {
                    lookup.publish(ListViewContract.EDIT_ELEMENT_REQUESTED, id);
                    onMessage.accept("Opening editor for item " + HtmlEscape.escape(id) + ".");
                } else {
                    onMessage.accept("No item selected to edit.");
                }
            }
            case "delete" -> {
                String name = (String) intent.params().get("name");
                if (name != null) {
                    Optional<Post> post = postService.findByTitle(name);
                    if (post.isPresent()) {
                        lookup.publish(ListViewContract.BULK_DELETE_REQUESTED, Set.of(post.get().id()));
                        onMessage.accept("Deleted '" + HtmlEscape.escape(name) + "'.");
                    } else {
                        onMessage.accept("Item '" + HtmlEscape.escape(name) + "' not found.");
                    }
                } else {
                    onMessage.accept("No item name specified for delete.");
                }
            }
            case "create" -> {
                lookup.publish(ListViewContract.CREATE_ELEMENT_REQUESTED);
                onMessage.accept("Opening create form.");
            }
            default -> onMessage.accept("Unknown action: " + HtmlEscape.escape(intent.action()));
        }
    }
}
