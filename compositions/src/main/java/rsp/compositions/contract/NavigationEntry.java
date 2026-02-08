package rsp.compositions.contract;

import rsp.component.ContextKey;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Slot;
import rsp.compositions.composition.ViewPlacement;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Framework-computed navigation metadata for PRIMARY contracts.
 * <p>
 * Each entry represents a unique navigable type (identified by typeHint) with its
 * route and display label. Computed at app startup from composition registrations.
 * <p>
 * Used by navigation/explorer UI components to render menus without
 * needing to access framework internals directly.
 *
 * @param typeHint  the contract's type hint (e.g., {@code Post.class})
 * @param label     display label from the contract's {@code title()} method
 * @param contractClass the contract class (for SET_PRIMARY events)
 * @param route     the route pattern (e.g., "/posts")
 */
public record NavigationEntry(Object typeHint,
                              String label,
                              Class<? extends ViewContract> contractClass,
                              String route) {

    /**
     * Compute navigation entries from all compositions.
     * <p>
     * Iterates PRIMARY placements across all compositions, instantiates each contract
     * via its factory to read {@code typeHint()}, deduplicates by typeHint, and resolves
     * routes from each composition's router.
     *
     * @param compositions the list of all app compositions
     * @return immutable list of unique navigation entries
     */
    public static List<NavigationEntry> fromCompositions(List<Composition> compositions) {
        if (compositions == null) {
            return List.of();
        }

        final Lookup metadataLookup = new MetadataLookup();
        final Map<Object, NavigationEntry> uniqueByHint = new LinkedHashMap<>();

        for (Composition comp : compositions) {
            for (ViewPlacement placement : comp.placementsForSlot(Slot.PRIMARY)) {
                try {
                    ViewContract contract = placement.contractFactory().apply(metadataLookup);
                    Object hint = contract.typeHint();

                    if (hint != null && !uniqueByHint.containsKey(hint)) {
                        String route = comp.router()
                                .findRoutePattern(placement.contractClass())
                                .orElse("/");
                        String label = contract.title();
                        uniqueByHint.put(hint, new NavigationEntry(hint, label, placement.contractClass(), route));
                    }
                } catch (Exception e) {
                    // Skip contracts that fail to instantiate for metadata extraction
                }
            }
        }

        return List.copyOf(uniqueByHint.values());
    }

    /**
     * Read-only Lookup used for metadata extraction.
     * <p>
     * Supports only {@code get()} operations. All mutation and event operations
     * throw {@link UnsupportedOperationException} to prevent side effects from
     * contracts that subscribe to events during construction.
     */
    private static final class MetadataLookup implements Lookup {

        @Override
        public <T> T get(Class<T> clazz) {
            return null;
        }

        @Override
        public <T> T get(ContextKey<T> key) {
            return null;
        }

        @Override
        public <T> T getRequired(ContextKey<T> key) {
            throw new IllegalStateException("MetadataLookup: no values available (metadata extraction only)");
        }

        @Override
        public <T> T getRequired(Class<T> clazz) {
            throw new IllegalStateException("MetadataLookup: no values available (metadata extraction only)");
        }

        @Override
        public <T> Lookup with(ContextKey<T> key, T value) {
            throw new UnsupportedOperationException("MetadataLookup does not support with");
        }

        @Override
        public <T> Lookup with(Class<T> clazz, T instance) {
            throw new UnsupportedOperationException("MetadataLookup does not support with");
        }

        @Override
        public <T> void publish(EventKey<T> eventKey, T payload) {
            throw new UnsupportedOperationException("MetadataLookup does not support publish");
        }

        @Override
        public void publish(EventKey.VoidKey eventKey) {
            throw new UnsupportedOperationException("MetadataLookup does not support publish");
        }

        @Override
        public <T> Registration subscribe(EventKey<T> eventKey, BiConsumer<String, T> handler) {
            throw new UnsupportedOperationException("MetadataLookup does not support subscribe");
        }

        @Override
        public Registration subscribe(EventKey.VoidKey eventKey, Runnable handler) {
            throw new UnsupportedOperationException("MetadataLookup does not support subscribe");
        }
    }
}
