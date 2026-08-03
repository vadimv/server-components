package rsp.compositions.contract;

import rsp.component.ComponentContext;
import rsp.component.Lookup;

import java.util.List;

/**
 * The runtime-facing capabilities of a routeable contract.
 *
 * <p>A contract is a component that owns its local state, intent handling,
 * lifecycle, and effects. Composition, routing, and agents depend on this
 * capability rather than on its rendered view.</p>
 */
public interface Contract {

    /**
     * Returns the lookup associated with the mounted contract.
     *
     * @return the live contract lookup
     */
    Lookup lookup();

    /**
     * Returns the title displayed for this contract.
     *
     * @return the contract title
     */
    String title();

    /**
     * Declares the actions available for agent invocation.
     *
     * @return declared actions, or an empty list
     */
    default List<ContractAction> agentActions() {
        return List.of();
    }

    /**
     * Returns structured metadata for agent consumers.
     *
     * @return metadata, or {@code null} when none is exposed
     */
    default ContractMetadata contractMetadata() {
        return null;
    }

    /**
     * Enriches context for descendants when a contract needs to expose data.
     *
     * @param context the current component context
     * @return the context for descendants
     */
    default ComponentContext enrichContext(ComponentContext context) {
        return context;
    }

    /**
     * Checks access before the contract is mounted.
     *
     * @param lookup lookup derived from the pending component context
     * @return whether access is allowed
     */
    default boolean isAuthorized(Lookup lookup) {
        AuthorizationStrategy strategy = lookup.get(ContextKeys.AUTHORIZATION_STRATEGY);
        return strategy == null || strategy.isAuthorized(this, lookup);
    }

    /** Strategy used to authorize contracts before they mount. */
    @FunctionalInterface
    interface AuthorizationStrategy {
        boolean isAuthorized(Contract contract, Lookup lookup);
    }
}
