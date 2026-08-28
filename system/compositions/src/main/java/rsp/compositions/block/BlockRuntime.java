package rsp.compositions.block;

import rsp.component.ComponentContext;
import rsp.component.Lookup;

import java.util.List;

/**
 * The narrow runtime-facing capabilities of a mounted block.
 *
 * <p>Composition and routing use the concrete {@link Block} type. Agent and
 * authorization integrations use this interface when they only need runtime
 * metadata, actions, lookup, and access checks.</p>
 */
public interface BlockRuntime {

    /**
     * Returns the lookup associated with the mounted block.
     *
     * @return the live block lookup
     */
    Lookup lookup();

    /**
     * Returns the title displayed for this block.
     *
     * @return the block title
     */
    String title();

    /**
     * Declares the actions available for agent invocation.
     *
     * @return declared actions, or an empty list
     */
    default List<BlockAction> agentActions() {
        return List.of();
    }

    /**
     * Returns structured metadata for agent consumers.
     *
     * @return metadata, or {@code null} when none is exposed
     */
    default BlockMetadata blockMetadata() {
        return null;
    }

    /**
     * Enriches context for descendants when a block needs to expose data.
     *
     * @param context the current component context
     * @return the context for descendants
     */
    default ComponentContext enrichContext(ComponentContext context) {
        return context;
    }

    /**
     * Checks access before the block is mounted.
     *
     * @param lookup lookup derived from the pending component context
     * @return whether access is allowed
     */
    default boolean isAuthorized(Lookup lookup) {
        AuthorizationStrategy strategy = lookup.get(ContextKeys.AUTHORIZATION_STRATEGY);
        return strategy == null || strategy.isAuthorized(this, lookup);
    }

    /** Strategy used to authorize blocks before they mount. */
    @FunctionalInterface
    interface AuthorizationStrategy {
        boolean isAuthorized(BlockRuntime block, Lookup lookup);
    }
}
