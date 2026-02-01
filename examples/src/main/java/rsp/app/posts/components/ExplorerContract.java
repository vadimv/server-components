package rsp.app.posts.components;

import rsp.component.ComponentContext;
import rsp.component.ContextKey;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Slot;
import rsp.compositions.composition.ViewPlacement;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.ViewContract;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * ExplorerContract - Provides navigation menu data based on typeHints from registered contracts.
 * <p>
 * This contract extracts unique typeHints from PRIMARY contracts across all compositions
 * and provides them to the ExplorerView for rendering as a navigation menu.
 * <p>
 * Register in LEFT_SIDEBAR slot:
 * <pre>{@code
 * places.place(Slot.LEFT_SIDEBAR, ExplorerContract.class, ExplorerContract::new)
 * }</pre>
 */
public class ExplorerContract extends ViewContract {

    private final List<ExplorerItem> items;
    private final Object activeHint;

    public ExplorerContract(Lookup lookup) {
        super(lookup);
        // Use a read-only lookup for extracting typeHints to avoid issues with
        // contracts that try to subscribe to events during instantiation
        Lookup readOnlyLookup = new ReadOnlyLookup(lookup);
        this.items = extractTypeHints(readOnlyLookup);
        this.activeHint = getActiveTypeHint(readOnlyLookup);
    }

    @Override
    public Object typeHint() {
        return ExplorerContract.class; // Explorer itself is a unique category
    }

    @Override
    public String title() {
        return "Explorer";
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        return context
                .with(ExplorerContextKeys.EXPLORER_ITEMS, items)
                .with(ExplorerContextKeys.EXPLORER_ACTIVE_HINT, activeHint)
                .with(ContextKeys.CONTRACT_TITLE, title());
    }

    /**
     * Extract unique typeHints from PRIMARY contracts across all compositions.
     */
    private List<ExplorerItem> extractTypeHints(Lookup readOnlyLookup) {
        List<Composition> compositions = readOnlyLookup.get(ContextKeys.APP_COMPOSITIONS);
        if (compositions == null) {
            return List.of();
        }

        Map<Object, ExplorerItem> uniqueByHint = new LinkedHashMap<>();

        for (Composition comp : compositions) {
            for (ViewPlacement placement : comp.placementsForSlot(Slot.PRIMARY)) {
                try {
                    // Create a temporary contract to get its typeHint
                    ViewContract contract = placement.contractFactory().apply(readOnlyLookup);
                    Object hint = contract.typeHint();

                    if (!uniqueByHint.containsKey(hint)) {
                        String route = comp.router()
                                .findRoutePattern(placement.contractClass())
                                .orElse("/");
                        String label = ExplorerItem.deriveLabel(hint);
                        uniqueByHint.put(hint, new ExplorerItem(hint, label, route));
                    }
                } catch (Exception e) {
                    // Skip contracts that fail to instantiate
                }
            }
        }

        return new ArrayList<>(uniqueByHint.values());
    }

    /**
     * Get the typeHint of the currently active PRIMARY contract.
     */
    private Object getActiveTypeHint(Lookup readOnlyLookup) {
        Class<? extends ViewContract> currentContractClass = readOnlyLookup.get(ContextKeys.ROUTE_CONTRACT_CLASS);
        if (currentContractClass == null) {
            return null;
        }

        List<Composition> compositions = readOnlyLookup.get(ContextKeys.APP_COMPOSITIONS);
        if (compositions == null) {
            return null;
        }

        for (Composition comp : compositions) {
            ViewPlacement placement = comp.placementFor(currentContractClass);
            if (placement != null && placement.slot() == Slot.PRIMARY) {
                try {
                    ViewContract contract = placement.contractFactory().apply(readOnlyLookup);
                    return contract.typeHint();
                } catch (Exception e) {
                    return null;
                }
            }
        }

        return null;
    }

    /**
     * Read-only Lookup that only supports get() operations.
     * Used for extracting typeHints without triggering event subscriptions.
     */
    private static final class ReadOnlyLookup implements Lookup {
        private final Lookup delegate;

        ReadOnlyLookup(Lookup delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> T get(Class<T> clazz) {
            return delegate.get(clazz);
        }

        @Override
        public <T> T get(ContextKey<T> key) {
            return delegate.get(key);
        }

        @Override
        public <T> T getRequired(ContextKey<T> key) {
            T value = get(key);
            if (value == null) {
                throw new IllegalStateException("Required value not found for key: " + key);
            }
            return value;
        }

        @Override
        public <T> T getRequired(Class<T> clazz) {
            T value = get(clazz);
            if (value == null) {
                throw new IllegalStateException("Required value not found for class: " + clazz);
            }
            return value;
        }

        @Override
        public <T> Lookup with(ContextKey<T> key, T value) {
            throw new UnsupportedOperationException("Read-only lookup does not support with");
        }

        @Override
        public <T> Lookup with(Class<T> clazz, T instance) {
            throw new UnsupportedOperationException("Read-only lookup does not support with");
        }

        @Override
        public <P> void publish(EventKey<P> eventKey, P payload) {
            throw new UnsupportedOperationException("Read-only lookup does not support publish");
        }

        @Override
        public void publish(EventKey.VoidKey eventKey) {
            throw new UnsupportedOperationException("Read-only lookup does not support publish");
        }

        @Override
        public <P> Registration subscribe(EventKey<P> eventKey, BiConsumer<String, P> handler) {
            throw new UnsupportedOperationException("Read-only lookup does not support subscribe");
        }

        @Override
        public Registration subscribe(EventKey.VoidKey eventKey, Runnable handler) {
            throw new UnsupportedOperationException("Read-only lookup does not support subscribe");
        }
    }
}
