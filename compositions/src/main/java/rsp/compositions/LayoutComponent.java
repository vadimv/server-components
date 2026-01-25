package rsp.compositions;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

import java.util.Map;
import java.util.function.BiFunction;

import static rsp.compositions.EventKeys.*;
import static rsp.dsl.Html.*;

/**
 * LayoutComponent - Renders UI components in layout slots with overlay support.
 * <p>
 * Provides rendering slots based on Slot enum:
 * <ul>
 *   <li><b>PRIMARY</b> - Main content area, always visible</li>
 *   <li><b>SECONDARY</b> - Optional secondary content (reserved for future use)</li>
 *   <li><b>OVERLAY</b> - Modal/dialog overlay, conditionally rendered</li>
 * </ul>
 * <p>
 * Overlay contracts (Slot.OVERLAY) are shown as popups/modals when triggered
 * by events. No URL change occurs when overlays open/close.
 * <p>
 * Listens for events:
 * <ul>
 *   <li>"openCreateModal" - Opens create modal</li>
 *   <li>"openEditModal" - Opens edit modal with entity ID</li>
 *   <li>"closeOverlay" - Closes the overlay</li>
 *   <li>"modalSaveSuccess" - Closes modal and triggers list refresh</li>
 *   <li>"modalDeleteSuccess" - Closes modal and triggers list refresh</li>
 * </ul>
 */
public class LayoutComponent extends Component<LayoutComponent.LayoutComponentState> {

    private final Component<?> primaryComponent;
    private final Map<Class<? extends ViewContract>, Component<?>> overlayComponents;
    private final Class<? extends ViewContract> autoOpenOverlay;
    private final String overlayRoutePattern;

    private Lookup lookup;

    /**
     * Creates a LayoutComponent with primary content only.
     *
     * @param primaryComponent The main UI component to render
     */
    public LayoutComponent(Component<?> primaryComponent) {
        this(primaryComponent, Map.of(), null, null);
    }

    /**
     * Creates a LayoutComponent with primary content and overlay components.
     *
     * @param primaryComponent The main UI component to render
     * @param overlayComponents Map of overlay components keyed by contract class
     */
    public LayoutComponent(Component<?> primaryComponent,
                           Map<Class<? extends ViewContract>, Component<?>> overlayComponents) {
        this(primaryComponent, overlayComponents, null, null);
    }

    /**
     * Creates a LayoutComponent with primary content, overlay components, and auto-open overlay.
     * <p>
     * Used when an OVERLAY contract is routed directly via URL (Case 2).
     * The overlay is auto-opened and URL is synced.
     *
     * @param primaryComponent The main UI component to render
     * @param overlayComponents Map of overlay components keyed by contract class
     * @param autoOpenOverlay Overlay to auto-activate (null for no auto-open)
     * @param overlayRoutePattern Route pattern for URL sync (e.g., "/posts/:id")
     */
    public LayoutComponent(Component<?> primaryComponent,
                           Map<Class<? extends ViewContract>, Component<?>> overlayComponents,
                           Class<? extends ViewContract> autoOpenOverlay,
                           String overlayRoutePattern) {
        super();
        this.primaryComponent = primaryComponent;
        this.overlayComponents = overlayComponents != null ? overlayComponents : Map.of();
        this.autoOpenOverlay = autoOpenOverlay;
        this.overlayRoutePattern = overlayRoutePattern;
    }

    @Override
    public ComponentStateSupplier<LayoutComponentState> initStateSupplier() {
        // If autoOpenOverlay is set, start with that overlay active (Case 2)
        return (_, _) -> new LayoutComponentState(
                primaryComponent, overlayComponents, autoOpenOverlay, overlayRoutePattern);
    }

    /**
     * Enrich context with active overlay contract's data.
     * This ensures the overlay's entity data is fresh when the overlay opens.
     */
    @Override
    public BiFunction<ComponentContext, LayoutComponentState, ComponentContext> subComponentsContext() {
        return (context, state) -> {
            Class<? extends ViewContract> activeOverlayClass = state.activeOverlayClass();
            if (activeOverlayClass == null) {
                return context; // No overlay active
            }

            // Get the overlay contract from context (stored by SceneComponent)
            ViewContract overlayContract = context.get(
                    ContextKeys.OVERLAY_VIEW_CONTRACT.with(activeOverlayClass.getName()));

            if (overlayContract == null) {
                return context; // Contract not found
            }

            // Re-enrich context with the overlay contract's data
            // This ensures item() is called with the current entity ID
            return overlayContract.enrichContext(context);
        };
    }

    @Override
    public ComponentSegment<LayoutComponentState> createComponentSegment(final QualifiedSessionId sessionId,
                                                                          final TreePositionPath componentPath,
                                                                          final TreeBuilderFactory treeBuilderFactory,
                                                                          final ComponentContext componentContext,
                                                                          final CommandsEnqueue commandsEnqueue) {
        // Create Lookup for use in view (for event publishing)
        this.lookup = createLookup(componentContext, commandsEnqueue);
        return super.createComponentSegment(sessionId, componentPath, treeBuilderFactory, componentContext, commandsEnqueue);
    }

    /**
     * Create a Lookup from ComponentContext for event publishing.
     */
    private Lookup createLookup(ComponentContext context, CommandsEnqueue commandsEnqueue) {
        Subscriber subscriber = context.get(Subscriber.class);
        if (subscriber == null) {
            // Fallback: create a no-op subscriber for publish-only usage
            subscriber = NoOpSubscriber.INSTANCE;
        }
        return new ContextLookup(context, commandsEnqueue, subscriber);
    }

    /**
     * No-op Subscriber for components that only need to publish events in componentView.
     */
    private static final class NoOpSubscriber implements Subscriber {
        static final NoOpSubscriber INSTANCE = new NoOpSubscriber();

        @Override
        public void addWindowEventHandler(String eventType, java.util.function.Consumer<rsp.page.EventContext> eventHandler,
                                          boolean preventDefault, rsp.dom.DomEventEntry.Modifier modifier) {}

        @Override
        public void addComponentEventHandler(String eventType, java.util.function.Consumer<ComponentEventEntry.EventContext> eventHandler,
                                             boolean preventDefault) {}
    }

    @Override
    public void onAfterRendered(LayoutComponentState state,
                                Subscriber subscriber,
                                CommandsEnqueue commandsEnqueue,
                                StateUpdate<LayoutComponentState> stateUpdate) {
        // Register handler for openCreateModal event
        subscriber.addEventHandler(OPEN_CREATE_MODAL, () -> {
            // Find CreateViewContract-based overlay
            Class<? extends ViewContract> createOverlayClass = findOverlayByBaseClass(
                    state.overlayComponents(), CreateViewContract.class);
            if (createOverlayClass != null) {
                stateUpdate.applyStateTransformation(s -> s.withActiveOverlay(createOverlayClass));
            }
        }, false);

        // Register handler for openEditModal event
        // For Case 2 (OVERLAY + route): also update URL
        subscriber.addEventHandler(OPEN_EDIT_MODAL, (eventName, entityId) -> {
            // Find EditViewContract-based overlay
            Class<? extends ViewContract> editOverlayClass = findOverlayByBaseClass(
                    state.overlayComponents(), EditViewContract.class);
            if (editOverlayClass != null) {
                // Check if we should sync URL (edit has route)
                Boolean editHasRoute = lookup.get(ContextKeys.EDIT_HAS_ROUTE);
                String editRoutePattern = lookup.get(ContextKeys.EDIT_ROUTE_PATTERN);

                if (editHasRoute != null && editHasRoute && editRoutePattern != null) {
                    // Case 2: Update URL to include entity ID
                    String editUrl = buildEditUrl(editRoutePattern, entityId);
                    lookup.publish(NAVIGATE, editUrl);
                    stateUpdate.applyStateTransformation(s ->
                            s.withActiveOverlayAndRoute(editOverlayClass, editRoutePattern));
                } else {
                    // Case 4: No URL change
                    stateUpdate.applyStateTransformation(s -> s.withActiveOverlay(editOverlayClass));
                }
            }
        }, false);

        // Register handler for closeOverlay event
        // For Case 2: restore URL to list view
        subscriber.addEventHandler(CLOSE_OVERLAY, () -> {
            handleOverlayClose(state, stateUpdate);
        }, false);

        // Register handler for modalSaveSuccess event (close modal + refresh list)
        subscriber.addEventHandler(MODAL_SAVE_SUCCESS, () -> {
            handleOverlayClose(state, stateUpdate);
            lookup.publish(REFRESH_LIST);
        }, false);

        // Register handler for modalDeleteSuccess event (close modal + refresh list)
        subscriber.addEventHandler(MODAL_DELETE_SUCCESS, () -> {
            handleOverlayClose(state, stateUpdate);
            lookup.publish(REFRESH_LIST);
        }, false);
    }

    /**
     * Handle overlay close with URL sync for Case 2.
     */
    private void handleOverlayClose(LayoutComponentState state,
                                    StateUpdate<LayoutComponentState> stateUpdate) {
        // If overlay has a route, navigate back to list URL
        if (state.hasOverlayRoute()) {
            // Navigate to parent route (remove the ID segment)
            String routePattern = state.overlayRoutePattern();
            String listRoute = getParentRoute(routePattern);
            if (listRoute != null) {
                lookup.publish(NAVIGATE, listRoute);
            }
        }
        stateUpdate.applyStateTransformation(s -> s.withActiveOverlay(null));
    }

    /**
     * Build edit URL by replacing :id in pattern with actual entity ID.
     */
    private String buildEditUrl(String pattern, String entityId) {
        return pattern.replace(":id", entityId);
    }

    /**
     * Get parent route by removing the last segment.
     * Example: "/posts/:id" → "/posts"
     */
    private String getParentRoute(String routePattern) {
        if (routePattern == null) return null;
        int lastSlash = routePattern.lastIndexOf('/');
        if (lastSlash <= 0) return "/";
        return routePattern.substring(0, lastSlash);
    }

    @Override
    public ComponentView<LayoutComponentState> componentView() {
        return _ -> state -> {
            Component<?> primary = state.primaryComponent();
            Class<? extends ViewContract> activeOverlayClass = state.activeOverlayClass();

            // Get active overlay component if any
            Component<?> activeOverlay = null;
            if (activeOverlayClass != null) {
                activeOverlay = state.overlayComponents().get(activeOverlayClass);
            }

            if (activeOverlay == null) {
                // No overlay - just render primary component
                return div(attr("class", "layout-container"),
                        div(attr("class", "layout-primary"),
                                primary
                        )
                );
            }

            // Render with overlay
            return div(attr("class", "layout-container"),
                    // Primary slot - always visible but may be behind overlay
                    div(attr("class", "layout-primary"),
                            primary
                    ),

                    // Overlay slot - modal overlay
                    renderOverlay(activeOverlay)
            );
        };
    }

    /**
     * Renders the overlay with backdrop and content.
     */
    private rsp.dsl.Definition renderOverlay(Component<?> content) {
        return div(attr("class", "modal-overlay"),
                // Semi-transparent backdrop - closes overlay on click
                div(attr("class", "modal-backdrop"),
                        on("click", ctx -> {
                            // Send closeOverlay event to trigger state change
                            lookup.publish(CLOSE_OVERLAY);
                        })
                ),
                // Modal content container
                div(attr("class", "modal-content"),
                        content
                )
        );
    }

    /**
     * Find an overlay by its base contract class.
     * Searches overlay components for one that extends the given base class.
     *
     * @param overlayComponents Map of overlay components
     * @param baseClass The base contract class to search for (e.g., EditViewContract.class)
     * @return The overlay contract class, or null if not found
     */
    private Class<? extends ViewContract> findOverlayByBaseClass(
            Map<Class<? extends ViewContract>, Component<?>> overlayComponents,
            Class<?> baseClass) {
        for (Class<? extends ViewContract> overlayClass : overlayComponents.keySet()) {
            if (baseClass.isAssignableFrom(overlayClass)) {
                return overlayClass;
            }
        }
        return null;
    }

    /**
     * State for LayoutComponent with slot-based overlay support.
     *
     * @param primaryComponent The main content component (always present)
     * @param overlayComponents Map of overlay components by contract class (Slot.OVERLAY placements)
     * @param activeOverlayClass The currently active overlay contract class (null = no overlay shown)
     * @param overlayRoutePattern Route pattern for URL-synced overlays (for restoring URL on close)
     */
    public record LayoutComponentState(
            Component<?> primaryComponent,
            Map<Class<? extends ViewContract>, Component<?>> overlayComponents,
            Class<? extends ViewContract> activeOverlayClass,
            String overlayRoutePattern
    ) {
        /**
         * Convenience constructor without route pattern.
         */
        public LayoutComponentState(Component<?> primaryComponent,
                                    Map<Class<? extends ViewContract>, Component<?>> overlayComponents,
                                    Class<? extends ViewContract> activeOverlayClass) {
            this(primaryComponent, overlayComponents, activeOverlayClass, null);
        }

        /**
         * Creates a new state with the specified overlay active.
         *
         * @param overlayClass The overlay contract class to show (null to close)
         * @return New state with overlay active/closed
         */
        public LayoutComponentState withActiveOverlay(Class<? extends ViewContract> overlayClass) {
            return new LayoutComponentState(primaryComponent, overlayComponents, overlayClass, overlayRoutePattern);
        }

        /**
         * Creates a new state with overlay and route pattern for URL sync.
         *
         * @param overlayClass The overlay contract class to show
         * @param routePattern The route pattern for URL sync
         * @return New state with overlay active and route pattern
         */
        public LayoutComponentState withActiveOverlayAndRoute(Class<? extends ViewContract> overlayClass,
                                                              String routePattern) {
            return new LayoutComponentState(primaryComponent, overlayComponents, overlayClass, routePattern);
        }

        /**
         * Check if any overlay is currently shown.
         *
         * @return true if overlay is active
         */
        public boolean hasOverlay() {
            return activeOverlayClass != null && overlayComponents.containsKey(activeOverlayClass);
        }

        /**
         * Check if this overlay has URL sync enabled.
         *
         * @return true if overlay route pattern is set
         */
        public boolean hasOverlayRoute() {
            return overlayRoutePattern != null && !overlayRoutePattern.isEmpty();
        }
    }
}
