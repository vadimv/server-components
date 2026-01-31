package rsp.compositions.layout;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.CreateViewContract;
import rsp.compositions.contract.EditViewContract;
import rsp.compositions.contract.ViewContract;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

import java.util.Map;
import java.util.function.BiFunction;

import static rsp.compositions.contract.ActionBindings.ShowPayload;
import static rsp.compositions.contract.EventKeys.*;
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

        @Override
        public void removeComponentEventHandler(String eventType) {}
    }

    @Override
    public void onAfterRendered(LayoutComponentState state,
                                Subscriber subscriber,
                                CommandsEnqueue commandsEnqueue,
                                StateUpdate<LayoutComponentState> stateUpdate) {
        // NO EVENT SUBSCRIPTIONS - Layout is purely reactive
        // SceneComponent handles all SHOW/HIDE events
    }


    @Override
    public ComponentView<LayoutComponentState> componentView() {
        return _ -> state -> {
            Component<?> primary = state.primaryComponent();
            Class<? extends ViewContract> activeOverlayClass = state.activeOverlayClass();

            // With on-demand instantiation: if no active overlay set but overlayComponents has entries,
            // automatically show the first one (there should only be one active at a time)
            if (activeOverlayClass == null && !state.overlayComponents().isEmpty()) {
                activeOverlayClass = state.overlayComponents().keySet().iterator().next();
            }

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
                    renderOverlay(activeOverlay, activeOverlayClass)
            );
        };
    }

    /**
     * Renders the overlay with backdrop and content.
     * Emits HIDE event with contract class when backdrop is clicked.
     */
    private rsp.dsl.Definition renderOverlay(Component<?> content, Class<? extends ViewContract> activeOverlayClass) {
        return div(attr("class", "modal-overlay"),
                // Semi-transparent backdrop - emits HIDE on click
                div(attr("class", "modal-backdrop"),
                        on("click", ctx -> {
                            // Emit HIDE event directly - SceneComponent will handle it
                            lookup.publish(HIDE, activeOverlayClass);
                        })
                ),
                // Modal content container
                div(attr("class", "modal-content"),
                        content
                )
        );
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
