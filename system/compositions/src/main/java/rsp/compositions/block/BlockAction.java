package rsp.compositions.block;

import rsp.component.EventKey;

import java.util.function.Function;

/**
 * Declares an action that can be invoked on a block.
 * <p>
 * Binds a human-readable action name to an {@link EventKey}, with a structured
 * {@link PayloadSchema} describing the expected payload. Blocks declare their
 * available actions via {@code blockActions()}, making the block the single
 * source of truth for what actions are available.
 * <p>
 * The payload parser is derived automatically from the schema unless explicitly overridden.
 * The {@link DispatchEffect} declares whether dispatching the action changes the
 * scene's routed block; the agent runtime uses this to gate plan iteration
 * on scene settlement rather than relying on timeouts.
 *
 * @param action       action name (e.g. "delete", "save", "page")
 * @param eventKey     the framework event to publish when this action is dispatched
 * @param description  human-readable purpose (e.g. "Delete items by their IDs")
 * @param schema       structured payload type descriptor; null treated as {@link PayloadSchema.None}
 * @param parsePayload converts an {@link BlockActionPayload} to the type expected by the event key;
 *                     throws {@link IllegalArgumentException} on unrecognized input
 * @param effect       what dispatching this action does to the routed block; null treated as
 *                     {@link DispatchEffect#NONE}
 */
public record BlockAction(String action,
                             EventKey<?> eventKey,
                             String description,
                             PayloadSchema schema,
                             Function<BlockActionPayload, Object> parsePayload,
                             DispatchEffect effect) {

    /**
     * Compact constructor — validates required fields, normalizes defaults.
     */
    public BlockAction {
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
        if (effect == null) {
            effect = DispatchEffect.NONE;
        }
    }

    /**
     * Convenience constructor with schema — parser derived automatically, effect defaulting to NONE.
     */
    public BlockAction(String action, EventKey<?> eventKey,
                          String description, PayloadSchema schema) {
        this(action, eventKey, description, schema, null, DispatchEffect.NONE);
    }

    /**
     * Convenience constructor for no-payload actions; effect defaulting to NONE.
     */
    public BlockAction(String action, EventKey<?> eventKey, String description) {
        this(action, eventKey, description, new PayloadSchema.None(), null, DispatchEffect.NONE);
    }

    /**
     * Convenience constructor with schema and explicit effect.
     */
    public BlockAction(String action, EventKey<?> eventKey,
                          String description, PayloadSchema schema, DispatchEffect effect) {
        this(action, eventKey, description, schema, null, effect);
    }

    /**
     * Convenience constructor for no-payload actions with explicit effect.
     */
    public BlockAction(String action, EventKey<?> eventKey, String description, DispatchEffect effect) {
        this(action, eventKey, description, new PayloadSchema.None(), null, effect);
    }
}
