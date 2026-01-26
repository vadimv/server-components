package rsp.compositions.contract;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.ContextLookup;
import rsp.component.Lookup;
import rsp.component.Subscriber;
import rsp.component.definitions.Component;
import rsp.compositions.auth.AuthorizationException;
import rsp.compositions.layout.LayoutComponent;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Slot;
import rsp.compositions.composition.UiRegistry;
import rsp.compositions.composition.ViewPlacement;
import rsp.compositions.routing.Router;
import rsp.dsl.Definition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static rsp.dsl.Html.*;

/**
 * SceneComponent - Builds Scene, resolves UI components, and renders the page.
 * <p>
 * This component:
 * <ol>
 *   <li>Reads composition and contract class from context (populated by RoutingComponent)</li>
 *   <li>Builds a Scene containing instantiated contracts in {@code initStateSupplier()}</li>
 *   <li>Stores the Scene in component state (contracts created once at mount)</li>
 *   <li>Enriches context with scene data for downstream UI components</li>
 *   <li>Resolves ViewContract classes to UI components via UiRegistry</li>
 *   <li>Renders the page structure with LayoutComponent</li>
 * </ol>
 * <p>
 * Position in component chain: RoutingComponent → SceneComponent → LayoutComponent
 * <p>
 * Slot-based overlay resolution:
 * When the primary contract has Slot.PRIMARY, any Slot.OVERLAY contracts in the same
 * composition are pre-instantiated for use as popup/modal overlays.
 * <p>
 * Benefits over transient context enrichment:
 * <ul>
 *   <li>Contracts instantiated once at mount, not every render cycle</li>
 *   <li>Scene is cacheable, testable, debuggable</li>
 *   <li>Explicit error handling via {@link Scene#isValid()}</li>
 * </ul>
 */
public class SceneComponent extends Component<Scene> {

    public SceneComponent() {
        super();
    }

    /**
     * Build the Scene at component mount time.
     * This is where contracts are instantiated and event handlers registered.
     */
    @Override
    public ComponentStateSupplier<Scene> initStateSupplier() {
        return (_, context) -> buildScene(context);
    }

    /**
     * Enrich context with scene data for downstream components.
     */
    @Override
    public java.util.function.BiFunction<ComponentContext, Scene, ComponentContext> subComponentsContext() {
        return (context, scene) -> {
            if (scene == null || !scene.isValid()) {
                return context; // Render will show error
            }

            ViewContract contract = scene.primaryContract();
            Composition composition = scene.composition();

            // Let the contract enrich context with its data (items, schema, etc.)
            ComponentContext enrichedContext = contract.enrichContext(context);

            // Add overlay contracts to context if present
            if (scene.hasOverlays()) {
                enrichedContext = enrichedContext.with(ContextKeys.OVERLAY_CONTRACTS, scene.overlayContracts());

                // Enrich context with each overlay contract's data
                for (ViewContract overlay : scene.overlayContracts().values()) {
                    enrichedContext = overlay.enrichContext(enrichedContext);
                    // Store the contract instance for event handling
                    enrichedContext = enrichedContext.with(
                            ContextKeys.OVERLAY_VIEW_CONTRACT.with(overlay.getClass().getName()),
                            overlay);
                }
            }

            // Add edit slot/route info to context for DefaultListView (use composition's router)
            enrichedContext = enrichEditSlotInfo(enrichedContext, composition, composition.router());

            return enrichedContext;
        };
    }

    /**
     * Add edit contract slot and route info to context.
     * This helps DefaultListView determine how to render the Edit button.
     */
    private ComponentContext enrichEditSlotInfo(ComponentContext context, Composition composition, Router router) {
        // Find EditViewContract-based placement in the composition
        ViewPlacement editPlacement = null;
        for (ViewPlacement placement : composition.views()) {
            if (EditViewContract.class.isAssignableFrom(placement.contractClass())) {
                editPlacement = placement;
                break;
            }
        }

        if (editPlacement == null) {
            return context; // No edit contract in this composition
        }

        Slot editSlot = editPlacement.slot();
        context = context.with(ContextKeys.EDIT_SLOT, editSlot);

        // Check if edit contract has a route
        boolean hasRoute = router != null && router.hasRoute(editPlacement.contractClass());
        context = context.with(ContextKeys.EDIT_HAS_ROUTE, hasRoute);

        // If it has a route, find the pattern
        if (hasRoute && router != null) {
            // Find route pattern by checking all routes
            for (ViewPlacement p : composition.views()) {
                if (EditViewContract.class.isAssignableFrom(p.contractClass())) {
                    // The edit route pattern typically follows pattern like "/entity/:id"
                    // We need to store this for building edit URLs
                    String listRoutePattern = context.get(ContextKeys.ROUTE_PATTERN);
                    if (listRoutePattern != null) {
                        // Assume edit pattern is list pattern + "/:id"
                        String editPattern = listRoutePattern + "/:id";
                        context = context.with(ContextKeys.EDIT_ROUTE_PATTERN, editPattern);
                    }
                    break;
                }
            }
        }

        return context;
    }

    @Override
    public ComponentView<Scene> componentView() {
        return _ -> scene -> {
            if (scene == null) {
                throw new IllegalStateException("Scene is null - check context setup");
            }

            if (scene.error() != null) {
                throw new IllegalStateException("Scene build failed", scene.error());
            }

            if (!scene.authorized()) {
                throw new AuthorizationException("Access denied: insufficient permissions");
            }

            // Get UiRegistry from scene state
            UiRegistry uiRegistry = scene.uiRegistry();
            if (uiRegistry == null) {
                throw new IllegalStateException("UiRegistry not found in scene");
            }

            // Resolve primary contract class to UI component
            Class<? extends ViewContract> contractClass = scene.primaryContract().getClass();
            Component<?> primaryComponent = resolveUiComponent(uiRegistry, contractClass);

            // Resolve overlay contracts to UI components
            Map<Class<? extends ViewContract>, Component<?>> overlayComponents = new HashMap<>();
            for (Class<? extends ViewContract> overlayClass : scene.overlayContracts().keySet()) {
                Component<?> overlayComponent = resolveUiComponent(uiRegistry, overlayClass);
                overlayComponents.put(overlayClass, overlayComponent);
            }

            // Render page with LayoutComponent, passing auto-open info from scene
            return page(primaryComponent, overlayComponents,
                    scene.autoOpenOverlay(), scene.overlayRoutePattern());
        };
    }

    /**
     * Renders the page structure with html, head, and body.
     */
    private static Definition page(Component<?> primaryComponent,
                                   Map<Class<? extends ViewContract>, Component<?>> overlayComponents,
                                   Class<? extends ViewContract> autoOpenOverlay,
                                   String overlayRoutePattern) {
        return html(head(title("Posts"),
                        link(attr("rel", "stylesheet"),
                             attr("href", "/res/style.css"))),
                body(new LayoutComponent(primaryComponent, overlayComponents,
                        autoOpenOverlay, overlayRoutePattern)));
    }

    /**
     * Resolves a ViewContract class to its UI implementation.
     * Walks up the class hierarchy to find a registered UI component.
     */
    private Component<?> resolveUiComponent(UiRegistry uiRegistry,
                                            Class<? extends ViewContract> contractClass) {
        // Try the contract class itself first
        Component<?> uiComponent = uiRegistry.resolve(contractClass);
        if (uiComponent != null) {
            return uiComponent;
        }

        // Walk up the inheritance hierarchy to find a registered base class
        Class<?> current = contractClass.getSuperclass();
        while (current != null && ViewContract.class.isAssignableFrom(current)) {
            @SuppressWarnings("unchecked")
            Class<? extends ViewContract> baseClass = (Class<? extends ViewContract>) current;
            uiComponent = uiRegistry.resolve(baseClass);
            if (uiComponent != null) {
                return uiComponent;
            }
            current = current.getSuperclass();
        }

        throw new IllegalStateException("No UI component registered for contract: " + contractClass.getName());
    }

    /**
     * Build the Scene from context.
     * Finds the composition, instantiates contracts, checks authorization.
     * Uses Slot-based resolution for overlays.
     * <p>
     * Handles 4 cases based on slot and route:
     * <ul>
     *   <li>Case 1: PRIMARY + route → full page edit, URL navigation</li>
     *   <li>Case 2: OVERLAY + route → modal with URL sync (auto-open overlay)</li>
     *   <li>Case 3: PRIMARY + no route → full page edit via event</li>
     *   <li>Case 4: OVERLAY + no route → modal via event only</li>
     * </ul>
     */
    private Scene buildScene(ComponentContext context) {
        try {
            // Read from context (populated by RoutingComponent)
            Composition composition = context.get(ContextKeys.ROUTE_COMPOSITION);
            Class<? extends ViewContract> contractClass = context.get(ContextKeys.ROUTE_CONTRACT_CLASS);
            UiRegistry uiRegistry = context.get(ContextKeys.UI_REGISTRY);
            String routePattern = context.get(ContextKeys.ROUTE_PATTERN);

            if (composition == null || contractClass == null) {
                return Scene.error(new IllegalStateException("Missing ROUTE_COMPOSITION or ROUTE_CONTRACT_CLASS in context"));
            }

            if (uiRegistry == null) {
                return Scene.error(new IllegalStateException("Missing UI_REGISTRY in context"));
            }

            // Router is inside the composition
            Router router = composition.router();

            // Get the placement for the routed contract
            ViewPlacement routedPlacement = composition.placementFor(contractClass);
            if (routedPlacement == null) {
                return Scene.error(new IllegalStateException("Contract not found in composition: " + contractClass.getName()));
            }

            Slot routedSlot = routedPlacement.slot();

            // Case 2: OVERLAY slot routed directly via URL
            // Need to find parent PRIMARY and auto-open this overlay
            if (routedSlot == Slot.OVERLAY) {
                return buildOverlayRoutedScene(context, composition, contractClass, routedPlacement,
                        routePattern, router, uiRegistry);
            }

            // Cases 1, 3: PRIMARY slot (with or without route)
            // Standard behavior: instantiate as primary, pre-instantiate OVERLAYs
            return buildPrimaryScene(context, composition, routedPlacement, uiRegistry);

        } catch (Exception e) {
            return Scene.error(e);
        }
    }

    /**
     * Build scene for OVERLAY contract routed directly via URL (Case 2).
     * Finds parent PRIMARY, uses it as primary, auto-opens the overlay.
     */
    private Scene buildOverlayRoutedScene(ComponentContext context, Composition composition,
                                          Class<? extends ViewContract> overlayContractClass,
                                          ViewPlacement overlayPlacement, String routePattern,
                                          Router router, UiRegistry uiRegistry) {
        // Find the parent PRIMARY contract
        ViewPlacement primaryPlacement = composition.primaryPlacement();
        if (primaryPlacement == null) {
            // Fallback: try to find parent route
            if (router != null && routePattern != null) {
                var parentRoute = router.findParentRoute(routePattern);
                if (parentRoute.isPresent()) {
                    primaryPlacement = composition.placementFor(parentRoute.get().contractClass());
                }
            }
        }

        if (primaryPlacement == null) {
            return Scene.error(new IllegalStateException(
                    "OVERLAY contract routed directly but no PRIMARY found in composition: " + composition.getClass().getName()));
        }

        // Instantiate primary contract
        ViewContract primaryContract = instantiatePlacement(primaryPlacement, context);
        if (primaryContract == null) {
            return Scene.error(new IllegalStateException(
                    "Failed to instantiate primary contract: " + primaryPlacement.contractClass().getName()));
        }

        // Check authorization
        if (!primaryContract.isAuthorized()) {
            return Scene.unauthorized(primaryContract, composition, uiRegistry);
        }

        // Pre-instantiate all OVERLAY contracts (including the routed one)
        Map<Class<? extends ViewContract>, ViewContract> overlayContracts = new HashMap<>();
        ComponentContext overlayContext = context.with(ContextKeys.IS_OVERLAY_MODE, true);

        for (ViewPlacement placement : composition.placementsForSlot(Slot.OVERLAY)) {
            Class<? extends ViewContract> overlayClass = placement.contractClass();
            if (overlayClass == null) continue;

            // For the auto-opened overlay, also set IS_AUTO_OPEN_OVERLAY = true
            ComponentContext contractContext = overlayClass.equals(overlayContractClass)
                    ? overlayContext.with(ContextKeys.IS_AUTO_OPEN_OVERLAY, true)
                    : overlayContext;

            ViewContract overlayContract = instantiatePlacement(placement, contractContext);
            if (overlayContract != null) {
                overlayContracts.put(overlayClass, overlayContract);
            }
        }

        // Return scene with auto-open overlay
        return Scene.withAutoOpenOverlay(primaryContract, composition, overlayContracts, uiRegistry,
                overlayContractClass, routePattern);
    }

    /**
     * Build scene for PRIMARY contract (Cases 1, 3).
     * Standard behavior: use as primary, pre-instantiate OVERLAYs.
     */
    private Scene buildPrimaryScene(ComponentContext context, Composition composition,
                                    ViewPlacement primaryPlacement, UiRegistry uiRegistry) {
        ViewContract contract = instantiatePlacement(primaryPlacement, context);
        if (contract == null) {
            return Scene.error(new IllegalStateException(
                    "Failed to instantiate contract: " + primaryPlacement.contractClass().getName()));
        }

        // Check authorization
        if (!contract.isAuthorized()) {
            return Scene.unauthorized(contract, composition, uiRegistry);
        }

        // Pre-instantiate OVERLAY contracts
        Map<Class<? extends ViewContract>, ViewContract> overlayContracts = Map.of();
        List<ViewPlacement> overlayPlacements = composition.placementsForSlot(Slot.OVERLAY);

        if (!overlayPlacements.isEmpty()) {
            overlayContracts = new HashMap<>();
            ComponentContext overlayContext = context.with(ContextKeys.IS_OVERLAY_MODE, true);

            for (ViewPlacement overlayPlacement : overlayPlacements) {
                Class<? extends ViewContract> overlayClass = overlayPlacement.contractClass();
                if (overlayClass == null) {
                    throw new IllegalStateException(
                            "ViewPlacement has null contractClass in composition: " + composition.getClass().getName());
                }
                ViewContract overlayContract = instantiatePlacement(overlayPlacement, overlayContext);
                if (overlayContract != null) {
                    overlayContracts.put(overlayClass, overlayContract);
                }
            }
        }

        return Scene.of(contract, composition, overlayContracts, uiRegistry);
    }

    /**
     * Instantiate a contract from its ViewPlacement.
     */
    private ViewContract instantiatePlacement(ViewPlacement placement, ComponentContext context) {
        Lookup lookup = createLookup(context);
        ViewContract contract = placement.contractFactory().apply(lookup);
        if (contract != null) {
            contract.registerHandlers();
        }
        return contract;
    }

    /**
     * Create a Lookup from ComponentContext for contract instantiation.
     * This bridges the framework infrastructure to the contract API.
     */
    private Lookup createLookup(ComponentContext context) {
        CommandsEnqueue commandsEnqueue = context.getRequired(CommandsEnqueue.class);
        Subscriber subscriber = context.getRequired(Subscriber.class);
        return new ContextLookup(context, commandsEnqueue, subscriber);
    }
}
