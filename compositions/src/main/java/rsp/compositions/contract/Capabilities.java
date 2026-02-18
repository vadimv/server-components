package rsp.compositions.contract;

import rsp.component.EventKey;

/**
 * Well-known capability keys for synchronous contract-to-contract negotiation.
 * <p>
 * Published via {@link ViewContract#publishCapability(EventKey, Object)} in the constructor,
 * consumed via {@link ViewContract#onCapability(EventKey, java.util.function.Consumer)}.
 * <p>
 * The same keys can be used with the async event pipeline for runtime capability changes.
 */
public final class Capabilities {
    private Capabilities() {}

    /**
     * The active category label (e.g., "Posts", "Comments").
     * Published by the primary contract; consumed by header/navigation components.
     */
    public static final EventKey.SimpleKey<String> ACTIVE_CATEGORY =
            new EventKey.SimpleKey<>("capability.activeCategory", String.class);
}
