package rsp.compositions.contract;

import java.util.HashMap;
import java.util.Map;

/**
 * ActionBindings - Maps abstract action names to target contract classes.
 * <p>
 * Views emit abstract actions (e.g., "edit", "create") via ACTION events.
 * Contracts declare bindings that translate these abstract actions to
 * concrete SHOW events targeting specific contract classes.
 * <p>
 * This decouples Views from concrete Contracts - Views don't know about
 * PostEditContract.class, they just emit ACTION("edit", {id: "123"}).
 * <p>
 * Example usage in a ListViewContract:
 * <pre>
 * {@code
 * @Override
 * protected ActionBindings actionBindings() {
 *     return ActionBindings.builder()
 *         .bind("edit", PostEditContract.class)
 *         .bind("create", PostCreateContract.class)
 *         .build();
 * }
 * }
 * </pre>
 */
public class ActionBindings {
    private final Map<String, ActionBinding> bindings;

    private ActionBindings(Map<String, ActionBinding> bindings) {
        this.bindings = bindings;
    }

    /**
     * Create empty bindings (no action translations).
     */
    public static ActionBindings empty() {
        return new ActionBindings(Map.of());
    }

    /**
     * Create a builder for fluent construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get binding for an action name.
     *
     * @param actionName The action name (e.g., "edit")
     * @return The binding, or null if not found
     */
    public ActionBinding get(String actionName) {
        return bindings.get(actionName);
    }

    /**
     * Check if a binding exists for an action name.
     *
     * @param actionName The action name
     * @return true if binding exists
     */
    public boolean has(String actionName) {
        return bindings.containsKey(actionName);
    }

    /**
     * Builder for fluent ActionBindings construction.
     */
    public static class Builder {
        private final Map<String, ActionBinding> bindings = new HashMap<>();

        /**
         * Bind an abstract action to a target contract class.
         *
         * @param actionName The action name (e.g., "edit", "create")
         * @param targetContract The contract class to show when this action is triggered
         * @return This builder for chaining
         */
        public Builder bind(String actionName, Class<? extends ViewContract> targetContract) {
            bindings.put(actionName, new ActionBinding(actionName, targetContract));
            return this;
        }

        /**
         * Build the immutable ActionBindings.
         */
        public ActionBindings build() {
            return new ActionBindings(Map.copyOf(bindings));
        }
    }

    /**
     * ActionBinding - Maps a single action name to a target contract class.
     *
     * @param actionName The action name (e.g., "edit", "create", "delete")
     * @param targetContract The contract class to show when this action is triggered
     */
    public record ActionBinding(
        String actionName,
        Class<? extends ViewContract> targetContract
    ) {}

    /**
     * ActionPayload - Data emitted with ACTION events.
     * <p>
     * Views emit ACTION events with this payload, containing:
     * - actionName: The abstract action (e.g., "edit")
     * - data: Action-specific data (e.g., {id: "123"} for edit)
     *
     * @param actionName The action name (e.g., "edit", "create")
     * @param data Additional data for the action (e.g., entity ID for edit)
     */
    public record ActionPayload(
        String actionName,
        Map<String, Object> data
    ) {
        /**
         * Create a payload with just an action name (no data).
         */
        public static ActionPayload of(String actionName) {
            return new ActionPayload(actionName, Map.of());
        }

        /**
         * Create a payload with action name and a single key-value pair.
         */
        public static ActionPayload of(String actionName, String key, Object value) {
            return new ActionPayload(actionName, Map.of(key, value));
        }
    }

    /**
     * ShowPayload - Data emitted with SHOW events.
     * <p>
     * Contracts emit SHOW events to trigger on-demand instantiation of other contracts.
     * SceneComponent handles these events and instantiates the target contract.
     *
     * @param contractClass The contract class to instantiate and show
     * @param data Data to pass to the contract (e.g., {id: "123"} for edit)
     */
    public record ShowPayload(
        Class<? extends ViewContract> contractClass,
        Map<String, Object> data
    ) {
        /**
         * Create a payload with just a contract class (no data).
         */
        public static ShowPayload of(Class<? extends ViewContract> contractClass) {
            return new ShowPayload(contractClass, Map.of());
        }

        /**
         * Create a payload with contract class and a single key-value pair.
         */
        public static ShowPayload of(Class<? extends ViewContract> contractClass, String key, Object value) {
            return new ShowPayload(contractClass, Map.of(key, value));
        }
    }
}
