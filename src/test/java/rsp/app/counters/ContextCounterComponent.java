package rsp.app.counters;

import rsp.component.ComponentView;
import rsp.component.definitions.ContextStateComponent;

import java.util.Objects;

/**
 * A counter component synchronized with the address bar (URL path or query parameters).
 * <p>
 * <strong>Usage examples:</strong>
 * <ul>
 *   <li>URL path element: {@code /100/1001} → counter 1 = 100, counter 2 = 1001</li>
 *   <li>URL query parameter: {@code ?c4=27} → counter 4 = 27</li>
 * </ul>
 *
 * @see ContextStateComponent for state-to-context synchronization
 * @see CountersView for the counter UI defintion and events handlers logic
 * @see CountersMainComponent for the counters group UI
 */
public class ContextCounterComponent extends ContextStateComponent<Integer> {

    private final String name;

    /**
     * Creates a context-synced counter for the given name.
     * <p>
     * The name is used as:
     * <ul>
     *   <li>Context attribute key (to retrieve state value from URL)</li>
     *   <li>Counter label in the UI</li>
     *   <li>HTML element ID prefix for event binding</li>
     * </ul>
     *
     * @param name the counter identifier (e.g., \"c1\", \"c2\", \"c4\")
     */
    public ContextCounterComponent(final String name) {
        super(name,
              Integer::parseInt,
              Object::toString);
        this.name = Objects.requireNonNull(name);
    }

    /**
     * Provides the view implementation for this counter.
     * <p>
     * This overrides the abstract {@link rsp.component.definitions.Component#componentView() componentView()}
     * method, allowing subclasses to specify their own view logic while inheriting state management
     * behavior from the base class.
     * <p>
     * The view reuses {@link CountersView}, which can be shared across multiple counter components
     * with different state synchronization strategies.
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
