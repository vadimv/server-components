package rsp.compositions.layout;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.ViewContract;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

import java.util.Map;
import java.util.function.BiFunction;

import static rsp.compositions.contract.EventKeys.*;
import static rsp.dsl.Html.*;

/**
 * LayoutComponent - Renders UI components in layout slots with overlay support.
 * <p>
 * Provides rendering slots based on Slot enum:
 * <ul>
 *   <li><b>PRIMARY</b> - Main content area, always visible</li>
 *   <li><b>LEFT_SIDEBAR</b> - Optional left sidebar, always visible alongside PRIMARY</li>
 *   <li><b>SECONDARY</b> - Optional secondary content (reserved for future use)</li>
 *   <li><b>OVERLAY</b> - Modal/dialog overlay, conditionally rendered</li>
 * </ul>
 * <p>
 * LEFT_SIDEBAR contracts are rendered to the left of PRIMARY content.
 * They are instantiated together with PRIMARY (not on-demand like OVERLAY).
 * <p>
 * Overlay contracts (Slot.OVERLAY) are shown as popups/modals when triggered
 * by events. No URL change occurs when overlays open/close.
 */
public class LayoutComponent extends Component<LayoutComponent.LayoutComponentState> {

    private final Component<?> primaryComponent;
    private final Component<?> leftSidebarComponent;
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
        this(primaryComponent, null, Map.of(), null, null);
    }

    /**
     * Creates a LayoutComponent with primary content and overlay components.
     *
     * @param primaryComponent The main UI component to render
     * @param overlayComponents Map of overlay components keyed by contract class
     */
    public LayoutComponent(Component<?> primaryComponent,
                           Map<Class<? extends ViewContract>, Component<?>> overlayComponents) {
        this(primaryComponent, null, overlayComponents, null, null);
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
        this(primaryComponent, null, overlayComponents, autoOpenOverlay, overlayRoutePattern);
    }

    /**
     * Creates a LayoutComponent with all slot options.
     *
     * @param primaryComponent The main UI component to render
     * @param leftSidebarComponent Optional left sidebar component (null for no sidebar)
     * @param overlayComponents Map of overlay components keyed by contract class
     * @param autoOpenOverlay Overlay to auto-activate (null for no auto-open)
     * @param overlayRoutePattern Route pattern for URL sync (e.g., "/posts/:id")
     */
    public LayoutComponent(Component<?> primaryComponent,
                           Component<?> leftSidebarComponent,
                           Map<Class<? extends ViewContract>, Component<?>> overlayComponents,
                           Class<? extends ViewContract> autoOpenOverlay,
                           String overlayRoutePattern) {
        super();
        this.primaryComponent = primaryComponent;
        this.leftSidebarComponent = leftSidebarComponent;
        this.overlayComponents = overlayComponents != null ? overlayComponents : Map.of();
        this.autoOpenOverlay = autoOpenOverlay;
        this.overlayRoutePattern = overlayRoutePattern;
    }

    @Override
    public ComponentStateSupplier<LayoutComponentState> initStateSupplier() {
        // If autoOpenOverlay is set, start with that overlay active (Case 2)
        return (_, _) -> new LayoutComponentState(
                primaryComponent, leftSidebarComponent, overlayComponents, autoOpenOverlay, overlayRoutePattern);
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
    }


    @Override
    public ComponentView<LayoutComponentState> componentView() {
        return _ -> state -> {
            Component<?> primary = state.primaryComponent();
            Component<?> leftSidebar = state.leftSidebarComponent();
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
                // No overlay - render primary component with optional sidebar
                if (leftSidebar == null) {
                    return div(attr("class", "layout-container"),
                            div(attr("class", "layout-primary"),
                                    primary
                            )
                    );
                }
                return div(attr("class", "layout-container"),
                        div(attr("class", "layout-sidebar"),
                                leftSidebar
                        ),
                        div(attr("class", "layout-primary"),
                                primary
                        )
                );
            }

            // Render with overlay (and optional sidebar)
            if (leftSidebar == null) {
                return div(attr("class", "layout-container"),
                        div(attr("class", "layout-primary"),
                                primary
                        ),
                        renderOverlay(activeOverlay, activeOverlayClass)
                );
            }
            return div(attr("class", "layout-container"),
                    div(attr("class", "layout-sidebar"),
                            leftSidebar
                    ),
                    div(attr("class", "layout-primary"),
                            primary
                    ),
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
     * State for LayoutComponent with slot-based layout support.
     *
     * @param primaryComponent The main content component (always present)
     * @param leftSidebarComponent Optional left sidebar component (null = no sidebar)
     * @param overlayComponents Map of overlay components by contract class (Slot.OVERLAY placements)
     * @param activeOverlayClass The currently active overlay contract class (null = no overlay shown)
     * @param overlayRoutePattern Route pattern for URL-synced overlays (for restoring URL on close)
     */
    public record LayoutComponentState(
            Component<?> primaryComponent,
            Component<?> leftSidebarComponent,
            Map<Class<? extends ViewContract>, Component<?>> overlayComponents,
            Class<? extends ViewContract> activeOverlayClass,
            String overlayRoutePattern
    ) {
        /**
         * Convenience constructor without sidebar and route pattern.
         */
        public LayoutComponentState(Component<?> primaryComponent,
                                    Map<Class<? extends ViewContract>, Component<?>> overlayComponents,
                                    Class<? extends ViewContract> activeOverlayClass) {
            this(primaryComponent, null, overlayComponents, activeOverlayClass, null);
        }

    }
}
