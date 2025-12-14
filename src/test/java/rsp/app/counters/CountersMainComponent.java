package rsp.app.counters;

import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentView;
import rsp.component.definitions.AddressBarSyncComponent;
import rsp.server.http.RelativeUrl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static rsp.dsl.Html.br;
import static rsp.dsl.Html.div;

/**
 * The main orchestrator component that synchronizes all counters with the browser's address bar.
 * <p>
 * <strong>Role:</strong> Extends {@link AddressBarSyncComponent} to coordinate multiple counter components
 * and synchronize their states with the URL.
 * <p>
 * <strong>Responsibilities:</strong>
 * <ul>
 *   <li>Define URL-to-state mappings for path elements and query parameters</li>
 *   <li>Create and compose child counter components</li>
 *   <li>Manage shared state storage for persistent counters</li>
 *   <li>Handle browser history (back/forward) navigation</li>
 * </ul>
 * <p>
 * <strong>URL Structure:</strong> {@code http://localhost:8085/:c1/:c2?c4=value}
 * <ul>
 *   <li>Path element 0 (position 0) → maps to context key \"c1\" → ContextCounterComponent(\"c1\")</li>
 *   <li>Path element 1 (position 1) → maps to context key \"c2\" → ContextCounterComponent(\"c2\")</li>
 *   <li>Query parameter \"c4\" → maps to context key \"c4\" → ContextCounterComponent(\"c4\")</li>
 *   <li>Persistent counter \"c3\" → managed by HideableCounterComponent with CachedCounterComponent</li>
 * </ul>
 * <p>
 * <strong>Component tree:</strong>
 * <pre>\n * CountersMainComponent (state = RelativeUrl)
 *   ├─ ContextCounterComponent(\"c1\")         → synced to path[0]
 *   ├─ ContextCounterComponent(\"c2\")         → synced to path[1]
 *   ├─ HideableCounterComponent                → conditionally shows c3
 *   │  └─ CachedCounterComponent(\"c3\")       → persistent state
 *   └─ ContextCounterComponent(\"c4\")         → synced to query param \"c4\"
 * </pre>
 * <p>
 * <strong>State synchronization flow:</strong>
 * <pre>
 * User clicks counter button
 *   ↓
 * Event bubbles to component
 *   ↓
 * Counter state changes (e.g., c1: 100 → 101)
 *   ↓
 * ContextStateComponent sends \"stateUpdated\" event up the tree
 *   ↓
 * AddressBarSyncComponent receives event and updates URL
 *   ↓
 * Browser address bar shows updated URL: /101/1001
 *   ↓
 * Browser history is updated for back/forward navigation
 * </pre>
 * <p>
 * <strong>Framework pattern:</strong> This class demonstrates the template method pattern:
 * {@link AddressBarSyncComponent} defines the framework (URL sync), this class defines the specifics
 * (which URL elements map to which context keys, and what child components to render).
 *
 * @see AddressBarSyncComponent for the URL synchronization framework
 * @see ContextCounterComponent for URL-synced counter implementation
 * @see CachedCounterComponent for persistent counter implementation
 * @see HideableCounterComponent for conditional rendering example
 */
public class CountersMainComponent extends AddressBarSyncComponent {

    private static final Map<ComponentCompositeKey, Integer> stateStore = new ConcurrentHashMap<>();

    /**
     * Creates the main orchestrator for the counters application.
     *
     * @param initialRelativeUrl the initial URL state, parsed from the browser's address bar
     */
    public CountersMainComponent(RelativeUrl initialRelativeUrl) {
        super(initialRelativeUrl);
    }

    /**
     * Defines which path elements in the URL map to which context keys.
     * <p>
     * Maps:
     * <ul>
     *   <li>path[0] → \"c1\" context key (used by ContextCounterComponent(\"c1\"))</li>
     *   <li>path[1] → \"c2\" context key (used by ContextCounterComponent(\"c2\"))</li>
     * </ul>
     *
     * @return the list of path element mappings
     */
    @Override
    public List<PositionKey> pathElementsPositionKeys() {
        return List.of(new PositionKey(0, "c1"), new PositionKey(1, "c2"));
    }

    /**
     * Defines which query parameters in the URL map to which context keys.
     * <p>
     * Maps:
     * <ul>
     *   <li>query param \"c4\" → \"c4\" context key (used by ContextCounterComponent(\"c4\"))</li>
     * </ul>
     *
     * @return the list of query parameter mappings
     */
    @Override
    public List<ParameterNameKey> queryParametersNamedKeys() {
        return List.of(new ParameterNameKey("c4", "c4"));
    }

    /**
     * Provides the view implementation for the main component.
     * <p>
     * The view receives the current URL state and returns a definition that composes
     * all child counter components. The URL context is passed down to children via
     * {@link AddressBarSyncComponent#subComponentsContext()}.
     *
     * @return a view that renders all counters with their current states
     *
     * @see ContextCounterComponent for URL-synced counters
     * @see HideableCounterComponent for conditional counter with persistence
     */
    @Override
    public ComponentView<RelativeUrl> componentView() {
        return _ ->_ -> div(new ContextCounterComponent("c1"),
                                                        br(),
                                                        new ContextCounterComponent("c2"),
                                                        br(),
                                                        new HideableCounterComponent("c3", stateStore),
                                                        new ContextCounterComponent("c4"));
    }
}
