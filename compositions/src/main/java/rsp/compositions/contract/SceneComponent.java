package rsp.compositions.contract;

import rsp.component.*;
import rsp.compositions.routing.AutoAddressBarSyncComponent;
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
import java.util.Map;
import java.util.Objects;
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

    private final Composition composition;
    private final Class<? extends ViewContract> contractClass;
    private final String routePattern;

    private ComponentContext savedContext;

    public SceneComponent(Composition composition,
                          Class<? extends ViewContract> contractClass,
                          String routePattern) {
        super();
        this.composition = Objects.requireNonNull(composition, "composition");
        this.contractClass = Objects.requireNonNull(contractClass, "contractClass");
        this.routePattern = Objects.requireNonNull(routePattern, "routePattern");
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

            // Let the primary contract enrich context with its data (items, schema, etc.)
            enrichedContext = contract.enrichContext(enrichedContext);

            // Add primary contract's typeHint to context for Explorer highlighting
            // This is dynamic - it updates when the primary contract changes via SET_PRIMARY
            Object primaryTypeHint = contract.typeHint();
            if (primaryTypeHint != null) {
                enrichedContext = enrichedContext.with(ContextKeys.PRIMARY_TYPE_HINT, primaryTypeHint);
            }

            // Let the LEFT_SIDEBAR contract enrich context with its data (if present)
            ViewContract leftSidebarContract = scene.leftSidebarContract();
            if (leftSidebarContract != null) {
                enrichedContext = leftSidebarContract.enrichContext(enrichedContext);
            }

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

                // Preserve primary contract's title before overlay enrichment
                // (overlay contracts may set their own CONTRACT_TITLE which would overwrite it)
                String primaryTitle = enrichedContext.get(ContextKeys.CONTRACT_TITLE);

                // Enrich context with each non-primary contract's data
                for (ViewContract nonPrimaryContract : nonPrimary.values()) {
                    enrichedContext = nonPrimaryContract.enrichContext(enrichedContext);
                    // Store the contract instance for event handling
                    enrichedContext = enrichedContext.with(
                            ContextKeys.OVERLAY_VIEW_CONTRACT.with(nonPrimaryContract.getClass().getName()),
                            nonPrimaryContract);
                }

                // Capture overlay title (set by overlay contracts during enrichment)
                // and store it in OVERLAY_TITLE for EditView to use
                String overlayTitle = enrichedContext.get(ContextKeys.CONTRACT_TITLE);
                if (overlayTitle != null && !overlayTitle.equals(primaryTitle)) {
                    enrichedContext = enrichedContext.with(ContextKeys.OVERLAY_TITLE, overlayTitle);
                }

                // Restore primary contract's title (the list view should show "Posts", not "Edit Post")
                if (primaryTitle != null) {
                    enrichedContext = enrichedContext.with(ContextKeys.CONTRACT_TITLE, primaryTitle);
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

        // If it has a route, derive the edit pattern from the constructor-provided routePattern
        if (hasRoute && router != null) {
            // Assume edit pattern is list pattern + "/:id"
            String editPattern = this.routePattern + "/:id";
            context = context.with(ContextKeys.EDIT_ROUTE_PATTERN, editPattern);
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

        subscriber.addEventHandler(SET_PRIMARY, (eventName, contractClass) -> {

            // Check if already active
            if (state.findContract(contractClass) != null) {
                return; // Already shown
            }

            if (state.primaryContract() != null) {
                state.primaryContract().onDestroy();
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

            // Update URL to reflect the new primary contract's route
            if (composition != null && composition.router() != null) {
                @SuppressWarnings("unchecked")
                Class<? extends ViewContract> typedContractClass = (Class<? extends ViewContract>) contractClass;
                String route = composition.router()
                    .findRoutePattern(typedContractClass)
                    .orElse(null);
                if (route != null) {
                    // To update browser URL
                    lookup.publish(AutoAddressBarSyncComponent.SET_PATH, route);
                }
            }

            stateUpdate.applyStateTransformation(s ->
                    s.withPrimaryContract(contract)
            );
        }, false);

        // ACTION_SUCCESS handler: framework-driven navigation based on contract placement
        subscriber.addEventHandler(ACTION_SUCCESS, (eventName, result) -> {
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
     * This follows the CountersMainComponent pattern:
     * - Contracts emit INTENT (action type only, no routes)
     * - Framework derives NAVIGATION from composition/router configuration
     * <p>
     * Framework behavior based on placement:
     * <ul>
     *   <li>OVERLAY → HIDE + legacy event + REFRESH_LIST</li>
     *   <li>PRIMARY (same contract) → scene rebuild (refresh in place)</li>
     *   <li>PRIMARY (different contract) → navigate to list route (derived from Router)</li>
     * </ul>
     * <p>
     * Benefits:
     * - Contracts truly route-agnostic (zero URL knowledge)
     * - Centralized navigation in framework
     * - Easy to extend (add new slots/behaviors without touching contracts)
     */
    private void handleActionSuccess(Scene state,
                                     ActionResult result,
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
            lookup.publish(HIDE, contractClass);

        } else {
            // PRIMARY behavior: derive navigation from composition
            // Case 1: Same contract as primary (e.g., bulk delete on list) → refresh in place
            if (contractClass.equals(state.primaryContract().getClass())) {
                Scene freshScene = buildScene(savedContext);
                stateUpdate.setState(freshScene);
            }
        }
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

            // Resolve LEFT_SIDEBAR contract to UI component (if present)
            Component<?> leftSidebarComponent = null;
            ViewContract leftSidebarContract = scene.leftSidebarContract();
            if (leftSidebarContract != null) {
                leftSidebarComponent = resolveUiComponent(uiRegistry, leftSidebarContract.getClass());
            }

            // Resolve active overlay contracts to UI components
            Map<Class<? extends ViewContract>, Component<?>> overlayComponents = new HashMap<>();
            for (Class<? extends ViewContract> overlayClass : scene.nonPrimaryContracts().keySet()) {
                Component<?> overlayComponent = resolveUiComponent(uiRegistry, overlayClass);
                overlayComponents.put(overlayClass, overlayComponent);
            }

            // Render page with LayoutComponent, passing auto-open info from scene
            return page(primaryComponent, leftSidebarComponent, overlayComponents,
                    scene.autoOpenContract(), scene.autoOpenRoutePattern(), scene.pageTitle());
        };
    }

    /**
     * Renders the page structure with html, head, and body.
     */
    private static Definition page(Component<?> primaryComponent,
                                   Component<?> leftSidebarComponent,
                                   Map<Class<? extends ViewContract>, Component<?>> overlayComponents,
                                   Class<? extends ViewContract> autoOpenContract,
                                   String autoOpenRoutePattern,
                                   String pageTitle) {
        Component<?> layoutComponent = new LayoutComponent(
                primaryComponent, leftSidebarComponent, overlayComponents, autoOpenContract, autoOpenRoutePattern);

        return html(head(title(pageTitle != null ? pageTitle : "App"),
                        link(attr("rel", "stylesheet"),
                             attr("href", "/res/style.css"))),
                body(layoutComponent));
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
            // composition, contractClass, routePattern come from constructor (validated with Objects.requireNonNull)
            // UI_REGISTRY still comes from context (set by AppComponent, not RoutingComponent)
            UiRegistry uiRegistry = context.get(ContextKeys.UI_REGISTRY);

            if (uiRegistry == null) {
                return Scene.error(new IllegalStateException("Missing UI_REGISTRY in context"));
            }

            // Router is inside the composition
            Router router = this.composition.router();

            // Get the placement for the routed contract
            ViewPlacement routedPlacement = this.composition.placementFor(this.contractClass);
            if (routedPlacement == null) {
                return Scene.error(new IllegalStateException("Contract not found in composition: " + this.contractClass.getName()));
            }

            Slot routedSlot = routedPlacement.slot();

            // Case 2: Non-primary slot routed directly via URL
            // Need to find parent PRIMARY and auto-open this non-primary contract
            if (routedSlot != Slot.PRIMARY) {
                return buildNonPrimaryRoutedScene(context,
                                                  this.composition,
                                                  this.contractClass,
                                                  routedPlacement,
                                                  this.routePattern,
                                                  router,
                                                  uiRegistry);
            }

            // Cases 1, 3: PRIMARY slot (with or without route)
            // Standard behavior: instantiate as primary, store factories for non-primary slots
            return buildPrimaryScene(context, this.composition, routedPlacement, uiRegistry);

        } catch (Exception e) {
            return Scene.error(e);
        }
    }

    /**
     * Build scene for non-primary contract routed directly via URL (Case 2).
     * Finds parent PRIMARY, uses it as primary, auto-opens this non-primary contract.
     * Also instantiates LEFT_SIDEBAR contracts (always visible).
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

        // Instantiate LEFT_SIDEBAR contracts (always visible, not on-demand)
        ViewContract leftSidebarContract = null;
        for (ViewPlacement placement : composition.views()) {
            if (placement.slot() == Slot.LEFT_SIDEBAR) {
                leftSidebarContract = instantiatePlacement(placement, context);
                break; // Only one LEFT_SIDEBAR contract expected
            }
        }

        // Build factories for on-demand slots (OVERLAY, etc. - excludes PRIMARY and LEFT_SIDEBAR)
        Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> nonPrimaryFactories = new HashMap<>();
        for (ViewPlacement placement : composition.views()) {
            if (placement.slot() != Slot.PRIMARY && placement.slot() != Slot.LEFT_SIDEBAR) {
                Class<? extends ViewContract> contractClass = placement.contractClass();
                if (contractClass != null) {
                    nonPrimaryFactories.put(contractClass, placement.contractFactory());
                }
            }
        }

        // Pre-instantiate the routed non-primary contract (auto-open case)
        Map<Class<? extends ViewContract>, ViewContract> activeNonPrimary = new HashMap<>();
        ComponentContext nonPrimaryContext = context
            .with(ContextKeys.CONTRACT_CLASS, nonPrimaryContractClass);

        ViewContract nonPrimaryContract = instantiatePlacement(nonPrimaryPlacement, nonPrimaryContext);
        if (nonPrimaryContract != null) {
            activeNonPrimary.put(nonPrimaryContractClass, nonPrimaryContract);
        }

        // Add LEFT_SIDEBAR to active contracts if present
        if (leftSidebarContract != null) {
            activeNonPrimary.put(leftSidebarContract.getClass(), leftSidebarContract);
        }

        // Return scene with auto-open non-primary contract
        return Scene.withAutoOpenContract(primaryContract, composition, nonPrimaryFactories, activeNonPrimary,
                uiRegistry, nonPrimaryContractClass, routePattern);
    }

    /**
     * Build scene for PRIMARY contract (Cases 1, 3, 4).
     * Standard behavior: use as primary, instantiate LEFT_SIDEBAR contracts,
     * store factories for other non-primary slots (OVERLAY etc. for on-demand instantiation).
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

        // Instantiate LEFT_SIDEBAR contracts (always visible, not on-demand)
        ViewContract leftSidebarContract = null;
        for (ViewPlacement placement : composition.views()) {
            if (placement.slot() == Slot.LEFT_SIDEBAR) {
                leftSidebarContract = instantiatePlacement(placement, context);
                break; // Only one LEFT_SIDEBAR contract expected
            }
        }

        // Build factories for on-demand slots (OVERLAY, etc.)
        Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> nonPrimaryFactories = new HashMap<>();

        for (ViewPlacement placement : composition.views()) {
            // Collect non-primary, non-sidebar placements for on-demand instantiation
            if (placement.slot() != Slot.PRIMARY && placement.slot() != Slot.LEFT_SIDEBAR) {
                Class<? extends ViewContract> contractClass = placement.contractClass();
                if (contractClass == null) {
                    throw new IllegalStateException(
                            "ViewPlacement has null contractClass in composition: " + composition.getClass().getName());
                }
                nonPrimaryFactories.put(contractClass, placement.contractFactory());
            }
        }

        // Use withLeftSidebar factory method if we have a sidebar, otherwise use of()
        if (leftSidebarContract != null) {
            return Scene.withLeftSidebar(contract, leftSidebarContract, composition, nonPrimaryFactories, uiRegistry);
        }
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
