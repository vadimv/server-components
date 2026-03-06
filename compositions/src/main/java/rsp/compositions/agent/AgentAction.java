package rsp.compositions.agent;

import rsp.component.EventKey;

/**
 * Declares an action that an agent can invoke on a contract.
 * <p>
 * Binds a human-readable action name to an {@link EventKey}, with descriptions
 * for both the action's purpose and its expected payload. Contracts declare their
 * available actions via {@code agentActions()}, making the contract the single
 * source of truth for what the agent can do.
 * <p>
 * The {@link IntentDispatcher} uses these declarations to look up and publish
 * the correct framework event — no hardcoded switch needed.
 *
 * @param action              action name used in {@link AgentIntent#action()} (e.g. "delete", "save", "page")
 * @param eventKey            the framework event to publish when this action is dispatched
 * @param description         human-readable purpose (e.g. "Delete items by their IDs")
 * @param payloadDescription  payload schema hint (e.g. "Set&lt;String&gt;: row IDs"), null for VoidKey events
 */
public record AgentAction(String action,
                           EventKey<?> eventKey,
                           String description,
                           String payloadDescription) {

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
}
