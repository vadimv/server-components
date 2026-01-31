package rsp.compositions.contract;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.auth.AuthorizationException;
import rsp.compositions.layout.LayoutComponent;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Slot;
import rsp.compositions.composition.UiRegistry;
import rsp.compositions.composition.ViewPlacement;
import rsp.compositions.routing.Router;
import rsp.compositions.contract.SlotUtils;
import rsp.dsl.Definition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static rsp.compositions.contract.ActionBindings.ShowPayload;
import static rsp.compositions.contract.EventKeys.*;
import static rsp.dsl.Html.*;

/**
 * SceneComponent - Builds Scene, resolves UI components, and renders the page.
 * <p>
 * This component:
 * <ol>
 *   <li>Reads composition and contract class from context (populated by RoutingComponent)</li>
 *   <li>Builds a Scene containing instantiated primary contract and factories for non-primary slots</li>
 *   <li>Handles SHOW events for on-demand contract instantiation</li>
 *   <li>Handles HIDE events for contract cleanup</li>
 *   <li>Enriches context with scene data for downstream UI components</li>
 *   <li>Resolves ViewContract classes to UI components via UiRegistry</li>
 *   <li>Renders the page structure with LayoutComponent</li>
 * </ol>
 * <p>
 * On-demand instantiation (per spec):
 * <ul>
 *   <li>Non-primary contracts are NOT pre-instantiated at mount time</li>
 *   <li>Factories are stored for lazy instantiation on SHOW events</li>
 *   <li>SHOW events trigger instantiation and add to active contracts</li>
 *   <li>HIDE events call onDestroy() and remove from active contracts</li>
 * </ul>
 * <p>
 * Slot-agnostic: Scene only distinguishes PRIMARY vs non-PRIMARY. Layout decides how to render each slot.
 * <p>
 * Position in component chain: RoutingComponent → SceneComponent → LayoutComponent
 */
public class SceneComponent extends Component<Scene> {

    private ComponentContext savedContext;

    public SceneComponent() {
        super();
    }

    /**
     * Build the Scene at component mount time.
     * Primary contract is instantiated, factories for non-primary slots are stored for lazy instantiation.
     */
    @Override
    public ComponentStateSupplier<Scene> initStateSupplier() {
        return (_, context) -> {
            this.savedContext = context;
            return buildScene(context);
        };
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

            // Add Scene to context so contracts can use SlotUtils
            ComponentContext enrichedContext = context.with(ContextKeys.SCENE, scene);

            // Let the contract enrich context with its data (items, schema, etc.)
            enrichedContext = contract.enrichContext(enrichedContext);

            // Add active contracts by slot to context (for Layout to read)
            enrichedContext = enrichedContext.with(
                ContextKeys.ACTIVE_CONTRACTS_BY_SLOT,
                scene.activeContractsBySlot().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().map(Scene.ActiveContract::contract).toList()
                    ))
            );

            // Add non-primary contracts to context if present (backward compatibility)
            if (scene.hasNonPrimaryContracts()) {
                Map<Class<? extends ViewContract>, ViewContract> nonPrimary = scene.nonPrimaryContracts();
                enrichedContext = enrichedContext.with(ContextKeys.OVERLAY_CONTRACTS, nonPrimary);

                // Enrich context with each non-primary contract's data
                for (ViewContract nonPrimaryContract : nonPrimary.values()) {
                    enrichedContext = nonPrimaryContract.enrichContext(enrichedContext);
                    // Store the contract instance for event handling
                    enrichedContext = enrichedContext.with(
                            ContextKeys.OVERLAY_VIEW_CONTRACT.with(nonPrimaryContract.getClass().getName()),
                            nonPrimaryContract);
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
            String listRoutePattern = context.get(ContextKeys.ROUTE_PATTERN);
            if (listRoutePattern != null) {
                // Assume edit pattern is list pattern + "/:id"
                String editPattern = listRoutePattern + "/:id";
                context = context.with(ContextKeys.EDIT_ROUTE_PATTERN, editPattern);
            }
        }

        return context;
    }

    /**
     * Register SHOW, HIDE, and ACTION_SUCCESS event handlers.
     * SHOW/HIDE manage on-demand contract lifecycle.
     * ACTION_SUCCESS enables framework-driven navigation (contracts emit generic success, framework decides what to do).
     */
    @Override
    public void onAfterRendered(Scene state,
                                Subscriber subscriber,
                                CommandsEnqueue commandsEnqueue,
                                StateUpdate<Scene> stateUpdate) {
        // SHOW handler: instantiate contract on-demand
        subscriber.addEventHandler(SHOW, (eventName, payload) -> {
            handleShowEvent(state, payload, stateUpdate, commandsEnqueue);
        }, false);

        // HIDE handler: destroy contract
        subscriber.addEventHandler(HIDE, (eventName, contractClass) -> {
            handleHideEvent(state, contractClass, stateUpdate);
        }, false);

        // ACTION_SUCCESS handler: framework-driven navigation based on contract placement
        subscriber.addEventHandler(EventKeys.ACTION_SUCCESS, (eventName, result) -> {
            handleActionSuccess(state, result, commandsEnqueue, stateUpdate);
        }, false);
    }

    /**
     * Handle SHOW event: instantiate contract on-demand and add to scene.
     */
    private void handleShowEvent(Scene state, ShowPayload payload,
                                 StateUpdate<Scene> stateUpdate,
                                 CommandsEnqueue commandsEnqueue) {
        Class<? extends ViewContract> contractClass = payload.contractClass();
        Map<String, Object> data = payload.data();

        // Check if already active
        if (state.findContract(contractClass) != null) {
            return; // Already shown
        }

        // Get factory from scene
        Function<Lookup, ViewContract> factory = state.getFactory(contractClass);
        if (factory == null) {
            // Not found in factories - check composition placements
            Composition composition = state.composition();
            ViewPlacement placement = composition != null ? composition.placementFor(contractClass) : null;
            if (placement != null) {
                factory = placement.contractFactory();
            }
        }

        if (factory == null) {
            return; // No factory available
        }

        // Resolve target slot from composition (no default assumption)
        Composition composition = state.composition();
        Slot targetSlot = null;
        if (composition != null) {
            ViewPlacement placement = composition.placementFor(contractClass);
            if (placement != null) {
                targetSlot = placement.slot();
            }
        }

        if (targetSlot == null) {
            // Fallback: if no placement found, cannot determine slot
            return;
        }

        // Create lookup with SHOW_DATA, marking contract as active
        // Note: Don't set IS_OVERLAY_MODE - Scene is slot-agnostic, contracts use SlotUtils if needed
        ComponentContext showContext = savedContext
            .with(ContextKeys.SHOW_DATA, data)
            .with(ContextKeys.CONTRACT_CLASS, contractClass)
            .with(ContextKeys.IS_ACTIVE_CONTRACT, true)  // Mark as active
            .with(ContextKeys.SCENE, state);  // Add Scene for SlotUtils

        Lookup lookup = createLookup(showContext, commandsEnqueue);

        // Instantiate contract
        ViewContract contract = factory.apply(lookup);
        if (contract == null) {
            return;
        }
        contract.registerHandlers();

        // Update state with active contract
        final Slot slot = targetSlot;
        stateUpdate.applyStateTransformation(s ->
            s.withActiveContract(slot, contract, contractClass, data)
        );
    }

    /**
     * Handle HIDE event: destroy contract and remove from scene.
     */
    private void handleHideEvent(Scene state,
                                 Class<? extends ViewContract> contractClass,
                                 StateUpdate<Scene> stateUpdate) {
        // Find the contract
        ViewContract contract = state.findContract(contractClass);
        if (contract == null) {
            return; // Not active
        }

        // Call cleanup hook
        contract.onDestroy();

        // Update state to remove contract
        stateUpdate.applyStateTransformation(s -> s.withContractClosed(contractClass));
    }

    /**
     * Handle ACTION_SUCCESS event: framework-driven navigation based on contract placement.
     * <p>
     * This enables complete separation of concerns:
     * - Contracts emit generic ACTION_SUCCESS events (no placement knowledge)
     * - Framework decides what to do based on contract's slot
     * - OVERLAY → HIDE + legacy event + REFRESH_LIST
     * - PRIMARY → NAVIGATE to target route
     * <p>
     * Benefits:
     * - Contracts truly placement-agnostic (zero navigation logic)
     * - Centralized navigation in framework
     * - Easy to extend (add new slots/behaviors without touching contracts)
     */
    private void handleActionSuccess(Scene state,
                                     EventKeys.ActionResult result,
                                     CommandsEnqueue commandsEnqueue,
                                     StateUpdate<Scene> stateUpdate) {
        // Get contract class from the action result
        Class<? extends ViewContract> contractClass = result.contractClass();
        if (contractClass == null) {
            return; // No contract class in result
        }

        // Create lookup for publishing events
        ComponentContext context = savedContext;
        if (context == null) {
            return; // No context available
        }
        Lookup lookup = createLookup(context, commandsEnqueue);

        // Determine behavior based on placement
        if (SlotUtils.isInOverlay(contractClass, state)) {
            // OVERLAY behavior: close overlay and refresh list
            lookup.publish(EventKeys.HIDE, contractClass);

            // Emit legacy events for backward compatibility
            if (result.type() == EventKeys.ActionType.SAVE) {
                lookup.publish(EventKeys.MODAL_SAVE_SUCCESS);
            } else if (result.type() == EventKeys.ActionType.DELETE) {
                lookup.publish(EventKeys.MODAL_DELETE_SUCCESS);
            }

            // Refresh list to show updated data
            lookup.publish(EventKeys.REFRESH_LIST);
        } else {
            // PRIMARY behavior: navigate to target route or refresh in place
            String targetRoute = result.targetRoute();
            String currentPath = lookup.get(ContextKeys.ROUTE_PATH);

            // Same URL (or both null): rebuild scene for SPA refresh
            // Different URL: navigate
            if (isSameRoute(targetRoute, currentPath)) {
                // Same URL: rebuild scene state directly (generic re-render mechanism)
                Scene freshScene = buildScene(savedContext);
                stateUpdate.setState(freshScene);
            } else if (targetRoute != null) {
                // Different URL: use NAVIGATE for SPA navigation
                lookup.publish(EventKeys.NAVIGATE, targetRoute);
            }
        }
    }

    /**
     * Check if two routes are the same (ignoring query params and handling nulls).
     */
    private boolean isSameRoute(String route1, String route2) {
        if (route1 == null && route2 == null) return true;
        if (route1 == null || route2 == null) return false;

        // Strip query params for comparison
        String path1 = route1.contains("?") ? route1.substring(0, route1.indexOf("?")) : route1;
        String path2 = route2.contains("?") ? route2.substring(0, route2.indexOf("?")) : route2;

        return path1.equals(path2);
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

            // Resolve active non-primary contracts to UI components
            Map<Class<? extends ViewContract>, Component<?>> nonPrimaryComponents = new HashMap<>();
            for (Class<? extends ViewContract> nonPrimaryClass : scene.nonPrimaryContracts().keySet()) {
                Component<?> nonPrimaryComponent = resolveUiComponent(uiRegistry, nonPrimaryClass);
                nonPrimaryComponents.put(nonPrimaryClass, nonPrimaryComponent);
            }

            // Render page with LayoutComponent, passing auto-open info from scene
            return page(primaryComponent, nonPrimaryComponents,
                    scene.autoOpenContract(), scene.autoOpenRoutePattern());
        };
    }

    /**
     * Renders the page structure with html, head, and body.
     * Passes components to Layout which decides how to render each slot.
     */
    private static Definition page(Component<?> primaryComponent,
                                   Map<Class<? extends ViewContract>, Component<?>> nonPrimaryComponents,
                                   Class<? extends ViewContract> autoOpenContract,
                                   String autoOpenRoutePattern) {
        return html(head(title("Posts"),
                        link(attr("rel", "stylesheet"),
                             attr("href", "/res/style.css"))),
                body(new LayoutComponent(primaryComponent, nonPrimaryComponents,
                        autoOpenContract, autoOpenRoutePattern)));
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
     * Finds the composition, instantiates primary contract, stores factories for non-primary slots.
     * <p>
     * Non-primary contracts are NOT pre-instantiated (on-demand instantiation via SHOW events).
     * Exception: Case 2 (non-primary routed via URL) - the routed non-primary contract is pre-instantiated.
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

            // Case 2: Non-primary slot routed directly via URL
            // Need to find parent PRIMARY and auto-open this non-primary contract
            if (routedSlot != Slot.PRIMARY) {
                return buildNonPrimaryRoutedScene(context, composition, contractClass, routedPlacement,
                        routePattern, router, uiRegistry);
            }

            // Cases 1, 3: PRIMARY slot (with or without route)
            // Standard behavior: instantiate as primary, store factories for non-primary slots
            return buildPrimaryScene(context, composition, routedPlacement, uiRegistry);

        } catch (Exception e) {
            return Scene.error(e);
        }
    }

    /**
     * Build scene for non-primary contract routed directly via URL (Case 2).
     * Finds parent PRIMARY, uses it as primary, auto-opens this non-primary contract.
     * Only the routed non-primary contract is pre-instantiated.
     */
    private Scene buildNonPrimaryRoutedScene(ComponentContext context, Composition composition,
                                             Class<? extends ViewContract> nonPrimaryContractClass,
                                             ViewPlacement nonPrimaryPlacement, String routePattern,
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
                    "Non-primary contract routed directly but no PRIMARY found in composition: " + composition.getClass().getName()));
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

        // Build factories for all non-primary slots (for on-demand instantiation)
        Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> nonPrimaryFactories = new HashMap<>();
        for (ViewPlacement placement : composition.views()) {
            if (placement.slot() != Slot.PRIMARY) {
                Class<? extends ViewContract> contractClass = placement.contractClass();
                if (contractClass != null) {
                    nonPrimaryFactories.put(contractClass, placement.contractFactory());
                }
            }
        }

        // Pre-instantiate ONLY the routed non-primary contract (auto-open case)
        Map<Class<? extends ViewContract>, ViewContract> activeNonPrimary = new HashMap<>();
        ComponentContext nonPrimaryContext = context
            .with(ContextKeys.CONTRACT_CLASS, nonPrimaryContractClass);
        // Note: Don't set IS_OVERLAY_MODE or IS_AUTO_OPEN_OVERLAY - Scene is slot-agnostic

        ViewContract nonPrimaryContract = instantiatePlacement(nonPrimaryPlacement, nonPrimaryContext);
        if (nonPrimaryContract != null) {
            activeNonPrimary.put(nonPrimaryContractClass, nonPrimaryContract);
        }

        // Return scene with auto-open non-primary contract
        return Scene.withAutoOpenContract(primaryContract, composition, nonPrimaryFactories, activeNonPrimary,
                uiRegistry, nonPrimaryContractClass, routePattern);
    }

    /**
     * Build scene for PRIMARY contract (Cases 1, 3, 4).
     * Standard behavior: use as primary, store factories for non-primary slots (no pre-instantiation).
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

        // Build factories for non-primary slots (for on-demand instantiation via SHOW events)
        Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> nonPrimaryFactories = new HashMap<>();

        for (ViewPlacement placement : composition.views()) {
            // Collect all non-primary placements
            if (placement.slot() != Slot.PRIMARY) {
                Class<? extends ViewContract> contractClass = placement.contractClass();
                if (contractClass == null) {
                    throw new IllegalStateException(
                            "ViewPlacement has null contractClass in composition: " + composition.getClass().getName());
                }
                nonPrimaryFactories.put(contractClass, placement.contractFactory());
            }
        }

        // No pre-instantiation - non-primary contracts created on-demand via SHOW events
        return Scene.of(contract, composition, nonPrimaryFactories, uiRegistry);
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

    /**
     * Create a Lookup with explicit CommandsEnqueue (for event handlers).
     */
    private Lookup createLookup(ComponentContext context, CommandsEnqueue commandsEnqueue) {
        Subscriber subscriber = context.getRequired(Subscriber.class);
        return new ContextLookup(context, commandsEnqueue, subscriber);
    }
}
