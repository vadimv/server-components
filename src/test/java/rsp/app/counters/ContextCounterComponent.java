package rsp.app.counters;

import rsp.component.ComponentView;
import rsp.component.definitions.ContextStateComponent;

import java.util.Objects;

/**
 * A counter component synchronized with the address bar (URL path or query parameters).
 * <p>
 * <strong>Composition pattern:</strong> Extends {@link ContextStateComponent} and overrides
 * {@link rsp.component.definitions.Component#componentView() componentView()} to provide
 * the counter's view implementation.
 * <p>
 * This demonstrates the <em>template method</em> pattern in the component framework:
 * <ul>
 *   <li><strong>Base class responsibility:</strong> {@link ContextStateComponent} handles state synchronization
 *      with context attributes (URL path elements or query parameters)</li>
 *   <li><strong>Subclass responsibility:</strong> {@code ContextCounterComponent} defines the view
 *      that renders the counter UI for those states</li>
 * </ul>
 * <p>
 * <strong>State flow:</strong>
 * <pre>
 * AddressBarSyncComponent (parent in tree) sets context attributes
 *   ↓
 * ContextStateComponent reads attribute value and parses it to state
 *   ↓
 * ContextCounterComponent.componentView() provides the view
 *   ↓
 * CountersView renders the counter UI
 *   ↓
 * On click: state changes and propagates back up to AddressBarSyncComponent
 *   ↓
 * AddressBarSyncComponent re-renders its subtree and updates URL and browser history
 * </pre>
 * <p>
 * <strong>Usage examples:</strong>
 * <ul>
 *   <li>URL path element: {@code /100/1001} → counter 1 = 100, counter 2 = 1001</li>
 *   <li>URL query parameter: {@code ?c4=27} → counter 4 = 27</li>
 * </ul>
 *
 * @see ContextStateComponent for state-to-context synchronization
 * @see CountersView for the UI rendering logic
 * @see CountersMainComponent for the parent orchestrator
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
