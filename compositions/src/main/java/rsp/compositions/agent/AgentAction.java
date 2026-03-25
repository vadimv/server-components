package rsp.compositions.agent;

import rsp.component.EventKey;

import java.util.function.Function;

/**
 * Declares an action that an agent can invoke on a contract.
 * <p>
 * Binds a human-readable action name to an {@link EventKey}, with descriptions
 * for both the action's purpose and its expected payload. Contracts declare their
 * available actions via {@code agentActions()}, making the contract the single
 * source of truth for what the agent can do.
 * <p>
 * The {@link ActionDispatcher} publishes the associated framework event directly.
 *
 * @param action              action name (e.g. "delete", "save", "page")
 * @param eventKey            the framework event to publish when this action is dispatched
 * @param description         human-readable purpose (e.g. "Delete items by their IDs")
 * @param payloadDescription  payload schema hint (e.g. "Set&lt;String&gt;: row IDs"), null for VoidKey events
 * @param parsePayload        converts an {@link AgentPayload} to the type expected by the event key;
 *                            throws {@link IllegalArgumentException} on unrecognized input
 */
public record AgentAction(String action,
                          EventKey<?> eventKey,
                          String description,
                          String payloadDescription,
                          Function<AgentPayload, Object> parsePayload) {

    /**
     * Compact constructor — validates required fields.
     */
    public AgentAction {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be null or blank");
        }
        if (eventKey == null) {
            throw new IllegalArgumentException("eventKey must not be null");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be null or blank");
        }
    }

    /**
     * Convenience constructor for actions with no payload (VoidKey events)
     * or when the payload value should be unwrapped to a plain Java object.
     */
    public AgentAction(String action, EventKey<?> eventKey,
                       String description, String payloadDescription) {
        this(action, eventKey, description, payloadDescription, p -> PayloadParsers.unwrap(p.value()));
    }
}
