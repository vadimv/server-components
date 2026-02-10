package rsp.compositions.contract;

import rsp.component.ComponentContext;
import rsp.component.EventKey;
import rsp.component.Lookup;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

public abstract class ViewContract {

    private Set<Lookup.Registration> handlerRegistrations = new HashSet<>();
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

    protected <T> void subscribe(EventKey<T> key, BiConsumer<String, T> handler) {
        handlerRegistrations.add(lookup.subscribe(key, handler));
    }

    protected <T> void subscribe(EventKey.VoidKey key, Runnable handler) {
        handlerRegistrations.add(lookup.subscribe(key, handler));
    }

    /**
     * Cleanup hook called when contract is hidden via HIDE event.
     * <p>
     * Override to perform cleanup logic such as:
     * <ul>
     *   <li>Unsubscribing from external event sources</li>
     *   <li>Releasing resources (connections, timers, etc.)</li>
     *   <li>Saving unsaved state</li>
     * </ul>
     * <p>
     * Called by SceneComponent when processing HIDE events.
     * The contract instance will be removed from active contracts after this call.
     */
    protected void onDestroy() {
        handlerRegistrations.forEach(registration -> registration.unsubscribe());
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
     * Enrich context with data needed by the view.
     * <p>
     * Called by ServicesComponent after contract instantiation.
     * Each contract type decides what data to provide:
     * <ul>
     *   <li>ListViewContract: items, schema, page, sort</li>
     *   <li>EditViewContract: entity, schema, listRoute, isCreateMode</li>
     *   <li>Future contracts: whatever they need</li>
     * </ul>
     * <p>
     * This is the **only** way contracts enrich context - no direct context storage of the contract itself.
     *
     * @param context The current context
     * @return Enriched context with view-specific data
     */
    public abstract ComponentContext enrichContext(ComponentContext context);

    /**
     * Get the contract's title.
     * Used to derive views' context-specific titles (list headers, form titles, page title).
     *
     * @return the contract's title
     */
    public abstract String title();

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
