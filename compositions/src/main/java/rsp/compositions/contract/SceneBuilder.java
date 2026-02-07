package rsp.compositions.contract;

import rsp.component.ComponentContext;
import rsp.component.Lookup;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Slot;
import rsp.compositions.composition.UiRegistry;
import rsp.compositions.composition.ViewPlacement;
import rsp.compositions.routing.Router;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Constructs Scene instances from composition configuration.
 * <p>
 * Reads composition, contract class, and route pattern; resolves UiRegistry from context;
 * instantiates primary contract and stores factories for non-primary slots.
 * <p>
 * Non-primary contracts are NOT pre-instantiated (on-demand via SHOW events).
 * Exception: non-primary contracts routed directly via URL are pre-instantiated.
 */
public final class SceneBuilder {

    private final Composition composition;
    private final Class<? extends ViewContract> contractClass;
    private final String routePattern;

    public SceneBuilder(Composition composition,
                        Class<? extends ViewContract> contractClass,
                        String routePattern) {
        this.composition = Objects.requireNonNull(composition, "composition");
        this.contractClass = Objects.requireNonNull(contractClass, "contractClass");
        this.routePattern = Objects.requireNonNull(routePattern, "routePattern");
    }

    /**
     * Build a complete Scene from the given context.
     * Determines whether the route targets a PRIMARY or non-PRIMARY slot
     * and delegates to the appropriate build method.
     */
    public Scene buildScene(ComponentContext context) {
        try {
            // UI_REGISTRY comes from context (set by AppComponent, not RoutingComponent)
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
     * Also instantiates sidebar contracts (always visible).
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

        // Instantiate sidebar contracts (always visible, not on-demand)
        ViewContract leftSidebarContract = null;
        ViewContract rightSidebarContract = null;
        for (ViewPlacement placement : composition.views()) {
            if (placement.slot() == Slot.LEFT_SIDEBAR && leftSidebarContract == null) {
                leftSidebarContract = instantiatePlacement(placement, context);
            } else if (placement.slot() == Slot.RIGHT_SIDEBAR && rightSidebarContract == null) {
                rightSidebarContract = instantiatePlacement(placement, context);
            }
        }

        // Build factories for on-demand slots (OVERLAY, etc. - excludes PRIMARY and sidebars)
        Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> nonPrimaryFactories = new HashMap<>();
        for (ViewPlacement placement : composition.views()) {
            if (placement.slot() != Slot.PRIMARY && placement.slot() != Slot.LEFT_SIDEBAR
                    && placement.slot() != Slot.RIGHT_SIDEBAR) {
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

        // Add sidebar contracts to active contracts if present
        if (leftSidebarContract != null) {
            activeNonPrimary.put(leftSidebarContract.getClass(), leftSidebarContract);
        }
        if (rightSidebarContract != null) {
            activeNonPrimary.put(rightSidebarContract.getClass(), rightSidebarContract);
        }

        // Return scene with auto-open non-primary contract
        return Scene.withAutoOpenContract(primaryContract, composition, nonPrimaryFactories, activeNonPrimary,
                uiRegistry, nonPrimaryContractClass, routePattern);
    }

    /**
     * Build scene for PRIMARY contract (Cases 1, 3, 4).
     * Standard behavior: use as primary, instantiate sidebar contracts,
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

        // Instantiate sidebar contracts (always visible, not on-demand)
        ViewContract leftSidebarContract = null;
        ViewContract rightSidebarContract = null;
        for (ViewPlacement placement : composition.views()) {
            if (placement.slot() == Slot.LEFT_SIDEBAR && leftSidebarContract == null) {
                leftSidebarContract = instantiatePlacement(placement, context);
            } else if (placement.slot() == Slot.RIGHT_SIDEBAR && rightSidebarContract == null) {
                rightSidebarContract = instantiatePlacement(placement, context);
            }
        }

        // Build factories for on-demand slots (OVERLAY, etc.)
        Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> nonPrimaryFactories = new HashMap<>();

        for (ViewPlacement placement : composition.views()) {
            // Collect non-primary, non-sidebar placements for on-demand instantiation
            if (placement.slot() != Slot.PRIMARY && placement.slot() != Slot.LEFT_SIDEBAR
                    && placement.slot() != Slot.RIGHT_SIDEBAR) {
                Class<? extends ViewContract> contractClass = placement.contractClass();
                if (contractClass == null) {
                    throw new IllegalStateException(
                            "ViewPlacement has null contractClass in composition: " + composition.getClass().getName());
                }
                nonPrimaryFactories.put(contractClass, placement.contractFactory());
            }
        }

        // Use appropriate factory method based on which sidebars are present
        if (leftSidebarContract != null || rightSidebarContract != null) {
            return Scene.withSidebars(contract, leftSidebarContract, rightSidebarContract,
                    composition, nonPrimaryFactories, uiRegistry);
        }
        return Scene.of(contract, composition, nonPrimaryFactories, uiRegistry);
    }

    /**
     * Instantiate a contract from its ViewPlacement.
     */
    ViewContract instantiatePlacement(ViewPlacement placement, ComponentContext context) {
        Lookup lookup = LookupFactory.create(context);
        ViewContract contract = placement.contractFactory().apply(lookup);
        if (contract != null) {
            contract.registerHandlers();
        }
        return contract;
    }
}
