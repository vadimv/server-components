package rsp.compositions.contract;

import rsp.component.ComponentContext;
import rsp.component.Lookup;
import rsp.server.http.AuthorizationException;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.UiRegistry;
import rsp.compositions.composition.ViewPlacement;
import rsp.compositions.layout.Layout;
import rsp.compositions.routing.Router;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Constructs Scene instances from composition configuration.
 * <p>
 * Lifecycle derivation:
 * <ul>
 *   <li>The routed contract (matched by RoutingComponent) is eagerly instantiated</li>
 *   <li>Contracts required by the Layout are eagerly instantiated (companions)</li>
 *   <li>All other contracts are stored as lazy factories (for on-demand SHOW events)</li>
 * </ul>
 * <p>
 * When the routed contract has a parent route (e.g., "/posts/:id" has parent "/posts"),
 * it is treated as an overlay-like contract: the parent is instantiated as the routed contract
 * and this contract is pre-activated for LayerComponent auto-open.
 * <p>
 * Throws {@link AuthorizationException} if the contract's authorization check fails.
 * Throws {@link IllegalStateException} if scene building fails for any other reason.
 */
public final class SceneBuilder {

    private final Composition composition;
    private final Class<? extends ViewContract> contractClass;
    private final String routePattern;
    private final Layout layout;

    public SceneBuilder(Composition composition,
                        Class<? extends ViewContract> contractClass,
                        String routePattern,
                        Layout layout) {
        this.composition = Objects.requireNonNull(composition, "composition");
        this.contractClass = Objects.requireNonNull(contractClass, "contractClass");
        this.routePattern = Objects.requireNonNull(routePattern, "routePattern");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    /**
     * Build a complete Scene from the given context.
     *
     * @throws AuthorizationException if the contract's authorization check fails
     * @throws IllegalStateException if scene building fails
     */
    public Scene buildScene(ComponentContext context) {
        UiRegistry uiRegistry = context.get(ContextKeys.UI_REGISTRY);
        if (uiRegistry == null) {
            throw new IllegalStateException("Missing UI_REGISTRY in context");
        }

        // Create a capability bus for synchronous capability negotiation between contracts
        CapabilityBus capabilityBus = new CapabilityBus();
        ComponentContext enrichedContext = context.with(CapabilityBus.class, capabilityBus);

        ViewPlacement routedPlacement = this.composition.placementFor(this.contractClass);
        if (routedPlacement == null) {
            throw new IllegalStateException("Contract not found in composition: " + this.contractClass.getName());
        }

        // Check if this contract has a parent route → overlay-like (auto-open case)
        Optional<Router.RouteMatch> parentRoute = composition.router().findParentRoute(routePattern);

        Scene scene;
        if (parentRoute.isPresent()) {
            scene = buildAutoOpenScene(enrichedContext, parentRoute.get(), routedPlacement, uiRegistry);
        } else {
            scene = buildStandardScene(enrichedContext, routedPlacement, uiRegistry);
        }

        // Resolve capabilities synchronously — all subscribers receive published values before rendering
        capabilityBus.resolve();

        return scene;
    }

    /**
     * Build scene for standard primary contract (no parent route).
     */
    private Scene buildStandardScene(ComponentContext context, ViewPlacement routedPlacement,
                                     UiRegistry uiRegistry) {
        ViewContract routedContract = instantiatePlacement(routedPlacement, context);
        if (routedContract == null) {
            throw new IllegalStateException("Failed to instantiate contract: " + routedPlacement.contractClass().getName());
        }

        if (!routedContract.isAuthorized()) {
            throw new AuthorizationException("Access denied: insufficient permissions for " + routedPlacement.contractClass().getName());
        }

        // Instantiate companion contracts (requested by Layout)
        Map<Class<? extends ViewContract>, ViewContract> companionContracts = instantiateCompanions(context);

        // Store remaining placements as lazy factories
        Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> lazyFactories =
                collectLazyFactories(this.contractClass, companionContracts);

        return Scene.of(routedContract, Map.copyOf(companionContracts), Map.copyOf(lazyFactories),
                composition, uiRegistry);
    }

    /**
     * Build scene for overlay-like contract routed directly via URL.
     * The parent contract becomes the routed contract; this contract is pre-activated for LayerComponent.
     */
    private Scene buildAutoOpenScene(ComponentContext context, Router.RouteMatch parentRoute,
                                     ViewPlacement overlayPlacement, UiRegistry uiRegistry) {
        // Find and instantiate the parent contract as the routed contract
        ViewPlacement parentPlacement = composition.placementFor(parentRoute.contractClass());
        if (parentPlacement == null) {
            throw new IllegalStateException(
                    "Parent contract not found in composition: " + parentRoute.contractClass().getName());
        }

        ViewContract parentContract = instantiatePlacement(parentPlacement, context);
        if (parentContract == null) {
            throw new IllegalStateException(
                    "Failed to instantiate parent contract: " + parentPlacement.contractClass().getName());
        }

        if (!parentContract.isAuthorized()) {
            throw new AuthorizationException(
                    "Access denied: insufficient permissions for " + parentPlacement.contractClass().getName());
        }

        // Instantiate companion contracts (requested by Layout)
        Map<Class<? extends ViewContract>, ViewContract> companionContracts = instantiateCompanions(context);

        // Pre-instantiate the overlay contract for LayerComponent auto-open
        ComponentContext overlayContext = context.with(ContextKeys.CONTRACT_CLASS, overlayPlacement.contractClass());
        ViewContract overlayContract = instantiatePlacement(overlayPlacement, overlayContext);
        Map<Class<? extends ViewContract>, ViewContract> preActivated = new HashMap<>();
        if (overlayContract != null) {
            preActivated.put(overlayPlacement.contractClass(), overlayContract);
        }

        // Store remaining placements as lazy factories (excludes parent, companions, and pre-activated)
        Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> lazyFactories = new HashMap<>();
        for (ViewPlacement placement : composition.views()) {
            Class<? extends ViewContract> placementClass = placement.contractClass();
            if (!placementClass.equals(parentPlacement.contractClass())
                    && !companionContracts.containsKey(placementClass)
                    && !preActivated.containsKey(placementClass)) {
                lazyFactories.put(placementClass, placement.contractFactory());
            }
        }

        return Scene.withAutoOpen(parentContract, Map.copyOf(companionContracts), Map.copyOf(lazyFactories),
                Map.copyOf(preActivated), composition, uiRegistry,
                new Scene.AutoOpen(overlayPlacement.contractClass(), routePattern));
    }

    /**
     * Instantiate companion contracts declared by the Layout.
     */
    private Map<Class<? extends ViewContract>, ViewContract> instantiateCompanions(ComponentContext context) {
        Set<Class<? extends ViewContract>> requiredByLayout = layout.requiredContracts();
        Map<Class<? extends ViewContract>, ViewContract> companions = new HashMap<>();

        for (ViewPlacement placement : composition.views()) {
            Class<? extends ViewContract> placementClass = placement.contractClass();
            if (requiredByLayout.contains(placementClass)) {
                ViewContract companion = instantiatePlacement(placement, context);
                if (companion != null) {
                    companions.put(placementClass, companion);
                }
            }
        }

        return companions;
    }

    /**
     * Collect lazy factories for all placements that are not the routed contract or companions.
     */
    private Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> collectLazyFactories(
            Class<? extends ViewContract> routedClass,
            Map<Class<? extends ViewContract>, ViewContract> companionContracts) {
        Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> lazyFactories = new HashMap<>();
        for (ViewPlacement placement : composition.views()) {
            Class<? extends ViewContract> placementClass = placement.contractClass();
            if (!placementClass.equals(routedClass) && !companionContracts.containsKey(placementClass)) {
                lazyFactories.put(placementClass, placement.contractFactory());
            }
        }
        return lazyFactories;
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
