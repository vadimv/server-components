package rsp.app.counters;

import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentView;
import rsp.component.definitions.StoredStateComponent;

import java.util.Map;

/**
 * A counter component with a temporary state storage.
 * <p>
 * <strong>Use case:</strong> The \"Show counter 3\" checkbox demonstrates this pattern.
 * When toggled off, the counter component is unmounted. When toggled back on,
 * the counter's state is restored rather than reset to the initial value.
 *
 * @see StoredStateComponent for the state persistence mechanism
 * @see CountersView for the UI rendering logic
 * @see HideableCounterComponent for the parent component that conditionally renders this
 */
public class CachedCounterComponent extends StoredStateComponent<Integer> {

    private final String name;

    /**
     * Creates a persistent counter with initial state and shared state store.
     *
     * @param name the counter identifier (used as label and HTML element ID prefix)
     * @param initialState the initial state value (used only if state is not found in store)
     * @param stateStore a thread-safe map for persisting counter states across component lifecycle
     */
    public CachedCounterComponent(String name, int initialState, Map<ComponentCompositeKey, Integer> stateStore) {
        super(initialState, stateStore);
        this.name = name;
    }

    /**
     * Provides the view implementation for this counter.
     * <p>
     * Reuses the same {@link CountersView} as other counter types, demonstrating that
     * the view can be decoupled from the state management strategy.
     *
     * @return a new CountersView instance configured for this counter's name
     *
     * @see CountersView for the UI implementation
     */
    @Override
    public ComponentView<Integer> componentView() {
        return new CountersView(this.name);
    }
}
