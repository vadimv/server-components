package rsp.compositions.contract;

import rsp.component.EventKey;

import java.util.function.Function;

/**
 * Declares an action that an agent can invoke on a contract.
 * <p>
 * Binds a human-readable action name to an {@link EventKey}, with a structured
 * {@link PayloadSchema} describing the expected payload. Contracts declare their
 * available actions via {@code agentActions()}, making the contract the single
 * source of truth for what the agent can do.
 * <p>
 * The payload parser is derived automatically from the schema unless explicitly overridden.
 * The {@link ActionDispatcher} publishes the associated framework event directly.
 *
 * @param action       action name (e.g. "delete", "save", "page")
 * @param eventKey     the framework event to publish when this action is dispatched
 * @param description  human-readable purpose (e.g. "Delete items by their IDs")
 * @param schema       structured payload type descriptor; null treated as {@link PayloadSchema.None}
 * @param parsePayload converts an {@link AgentPayload} to the type expected by the event key;
 *                     throws {@link IllegalArgumentException} on unrecognized input
 */
public record AgentAction(String action,
                          EventKey<?> eventKey,
                          String description,
                          PayloadSchema schema,
                          Function<AgentPayload, Object> parsePayload) {

    /**
     * Compact constructor — validates required fields, normalizes defaults.
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
        if (schema == null) {
            schema = new PayloadSchema.None();
        }
        if (parsePayload == null) {
            parsePayload = PayloadSchemas.toParser(schema);
        }
    }

    /**
     * Convenience constructor with schema — parser derived automatically.
     */
    public AgentAction(String action, EventKey<?> eventKey,
                       String description, PayloadSchema schema) {
        this(action, eventKey, description, schema, null);
    }

    /**
     * Convenience constructor for no-payload actions.
     */
    public AgentAction(String action, EventKey<?> eventKey, String description) {
        this(action, eventKey, description, new PayloadSchema.None(), null);
    }
}
