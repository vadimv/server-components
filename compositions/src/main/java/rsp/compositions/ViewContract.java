package rsp.compositions;

import rsp.component.Lookup;

public abstract class ViewContract {

    protected final Lookup lookup;

    protected ViewContract(Lookup lookup) {
        this.lookup = lookup;
    }

    protected <T> T resolve(QueryParam<T> param) {
        return param.resolve(lookup);
    }

    protected <T> T resolve(PathParam<T> param) {
        return param.resolve(lookup);
    }

    /**
     * Register event handlers for this contract.
     * <p>
     * Override to subscribe to events from the associated view.
     * Called during contract initialization.
     */
    protected void registerHandlers() {
        // Default: no handlers - override in subclasses
    }

    /**
     * Override to implement custom authorization logic.
     * Default: delegates to auth.strategy from context if present,
     * otherwise allows all (public access).
     * <p>
     * Most contracts should rely on the global strategy from context.
     * Override only for contract-specific authorization requirements.
     *
     * @return true if user is authorized to access this view, false otherwise
     */
    protected boolean isAuthorized() {
        AuthorizationStrategy strategy = lookup.get(ContextKeys.AUTHORIZATION_STRATEGY);
        return strategy == null || strategy.isAuthorized(this, lookup);
    }

    /**
     * Pluggable authorization strategy interface.
     * <p>
     * Applications register a strategy in context via "auth.strategy" key.
     * The strategy receives the contract and lookup, and decides authorization.
     * <p>
     * Example strategies:
     * <ul>
     * <li>RBAC - Check user roles against contract requirements</li>
     * <li>Permission-based - Check user permissions</li>
     * <li>Attribute-based - Complex rules based on user/resource attributes</li>
     * <li>Custom - Any authorization logic</li>
     * </ul>
     */
    public interface AuthorizationStrategy {
        /**
         * Determine if user is authorized to access the given contract.
         *
         * @param contract The contract being accessed
         * @param lookup The lookup containing auth data
         * @return true if authorized, false otherwise
         */
        boolean isAuthorized(ViewContract contract, Lookup lookup);
    }
}
