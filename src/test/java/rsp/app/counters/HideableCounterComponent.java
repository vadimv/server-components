package rsp.app.counters;

import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.StateUpdate;
import rsp.component.definitions.Component;
import rsp.page.EventContext;

import java.util.Map;
import java.util.function.Consumer;

import static rsp.dsl.Html.*;

/**
 * A component that conditionally renders a persistent counter based on a checkbox.
 * <p>
 * <strong>Pattern demonstrated:</strong> Conditional rendering with state persistence.
 * This component shows how to:
 * <ul>
 *   <li>Use component state (boolean) to control visibility of child components</li>
 *   <li>Preserve child component state across mount/unmount cycles</li>
 *   <li>Share state storage between parent and child</li>
 * </ul>
 * <p>
 * <strong>Component structure:</strong>
 * <pre>
 * HideableCounterComponent (state = boolean)
 *   ├─ CachedCounterComponent (state = integer, stored persistently)
 *   └─ Checkbox (controls visibility of counter)
 * </pre>
 * <p>
 * <strong>Interaction flow:</strong>
 * <ul>
 *   <li><strong>Checkbox unchecked:</strong> CachedCounterComponent is unmounted (hidden from view)</li>
 *   <li><strong>Checkbox checked:</strong> CachedCounterComponent is remounted, restores its state from store</li>
 *   <li><strong>No initial state reset:</strong> Counter shows its last value, not reset to initial</li>
 * </ul>
 * <p>
 * <strong>The conditional rendering pattern:</strong>
 * The view uses {@code when(state, definition)} to conditionally include components:
 * <pre>
 * when(state, new CachedCounterComponent(...))  // Only includes if state is true
 * </pre>
 *
 * @see CachedCounterComponent for the persistent counter implementation
 * @see Component for the base class and component lifecycle
 * @see CountersMainComponent for the parent that orchestrates all counters
 */
public class HideableCounterComponent extends Component<Boolean> {

    private final Map<ComponentCompositeKey, Integer> stateStore;

    /**
     * Creates a hideable counter component.
     *
     * @param name the counter identifier (passed to the child CachedCounterComponent)
     * @param stateStore shared state store for persisting counter values across visibility toggles
     */
    public HideableCounterComponent(String name, Map<ComponentCompositeKey, Integer> stateStore) {
        super(HideableCounterComponent.class);
        this.stateStore =    stateStore;
    }

    /**
     * Provides the initial state supplier.
     * <p>
     * Initializes with {@code true}, so the counter is visible by default.
     *
     * @return a supplier that always returns true for initial visibility
     */
    @Override
    public ComponentStateSupplier<Boolean> initStateSupplier() {
        return (_, _) -> true;
    }

    /**
     * Provides the view implementation for this hideable counter.
     * <p>
     * The view is a {@link ComponentView} that takes a state updater and returns a View function.
     * The View function conditionally includes the counter based on the boolean state.
     *
     * @return a view that renders the counter conditionally and a checkbox to control visibility
     *
     * @see CachedCounterComponent for the child component
     */
    @Override
    public ComponentView<Boolean> componentView() {
        return newState -> state ->
                div(
                        when(state, new CachedCounterComponent("c3", 101, stateStore)),
                        input(attr("type", "checkbox"),
                                when(state, attr("checked", "checked")),
                                attr("id","c3"),
                                attr("name", "c3"),
                                on("click", checkboxClickHandler(state, newState))),
                        label(attr("for", "c3"),
                                text("Show counter 3"))
                );
    }

    private static Consumer<EventContext> checkboxClickHandler(Boolean state, StateUpdate<Boolean> newState) {
        return  _ -> newState.setState(!state);
    }
}
