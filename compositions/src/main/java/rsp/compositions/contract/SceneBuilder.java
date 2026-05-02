package rsp.compositions.contract;

import rsp.component.ComponentContext;
import rsp.component.Lookup;
import rsp.server.http.AuthorizationException;
import rsp.compositions.application.ServicesLifecycleHandler;
import rsp.compositions.application.Services;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.layout.Layout;
import rsp.compositions.routing.Router;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        Group contracts = composition.contracts();

        // Verify contract is registered
        if (contracts.contractFactory(this.contractClass) == null) {
            throw new IllegalStateException("Contract not found in composition: " + this.contractClass.getName());
        }

        // Check if this contract has a parent route → overlay-like (auto-open case)
        Optional<Router.RouteMatch> parentRoute = composition.router().findParentRoute(routePattern);

        Scene scene;
        if (parentRoute.isPresent()) {
            scene = buildAutoOpenScene(context, parentRoute.get());
        } else {
            scene = buildStandardScene(context);
        }

        startServicesLifecycleHandlers(context);

        return scene;
    }

    /**
     * Build scene for standard primary contract (no parent route).
     */
    private Scene buildStandardScene(ComponentContext context) {
        Group contracts = composition.contracts();
        Function<Lookup, ViewContract> routedFactory = contracts.contractFactory(this.contractClass);

        ContractRuntime routedRuntime = instantiateContract(this.contractClass, routedFactory, context);
        if (routedRuntime == null) {
            throw new IllegalStateException("Failed to instantiate contract: " + this.contractClass.getName());
        }

        if (!routedRuntime.contract().isAuthorized()) {
            throw new AuthorizationException("Access denied: insufficient permissions for " + this.contractClass.getName());
        }

        // Instantiate companion contracts (requested by Layout)
        Map<Class<? extends ViewContract>, ContractRuntime> companionRuntimes = instantiateCompanions(context);

        // Store remaining contract classes as lazy factories
        Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> lazyFactories =
                collectLazyFactories(this.contractClass, companionRuntimes);

        return Scene.of(routedRuntime, orderedUnmodifiableCopy(companionRuntimes), Map.copyOf(lazyFactories), composition);
    }

    /**
     * Build scene for overlay-like contract routed directly via URL.
     * The parent contract becomes the routed contract; this contract is pre-activated for LayerComponent.
     */
    private Scene buildAutoOpenScene(ComponentContext context, Router.RouteMatch parentRoute) {
        Group contracts = composition.contracts();

        // Overlay contract factory
        Function<Lookup, ViewContract> overlayFactory = contracts.contractFactory(this.contractClass);
        if (overlayFactory == null) {
            throw new IllegalStateException("Overlay contract not found: " + this.contractClass.getName());
        }

        // Find and instantiate the parent contract as the routed contract
        Class<? extends ViewContract> parentClass = parentRoute.contractClass();
        Function<Lookup, ViewContract> parentFactory = contracts.contractFactory(parentClass);
        if (parentFactory == null) {
            throw new IllegalStateException(
                    "Parent contract not found in composition: " + parentClass.getName());
        }

        ContractRuntime parentRuntime = instantiateContract(parentClass, parentFactory, context);
        if (parentRuntime == null) {
            throw new IllegalStateException(
                    "Failed to instantiate parent contract: " + parentClass.getName());
        }

        if (!parentRuntime.contract().isAuthorized()) {
            throw new AuthorizationException(
                    "Access denied: insufficient permissions for " + parentClass.getName());
        }

        // Instantiate companion contracts (requested by Layout)
        Map<Class<? extends ViewContract>, ContractRuntime> companionRuntimes = instantiateCompanions(context);

        // Pre-instantiate the overlay contract for LayerComponent auto-open
        ComponentContext overlayContext = context.with(ContextKeys.CONTRACT_CLASS, this.contractClass);
        ContractRuntime overlayRuntime = instantiateContract(this.contractClass, overlayFactory, overlayContext);
        Map<Class<? extends ViewContract>, ContractRuntime> preActivated = new HashMap<>();
        if (overlayRuntime != null) {
            preActivated.put(this.contractClass, overlayRuntime);
        }

        // Store remaining contract classes as lazy factories (excludes parent, companions, pre-activated)
        Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> lazyFactories = new HashMap<>();
        for (Class<? extends ViewContract> cls : contracts.contractClasses()) {
            if (!cls.equals(parentClass)
                    && !companionRuntimes.containsKey(cls)
                    && !preActivated.containsKey(cls)) {
                lazyFactories.put(cls, contracts.contractFactory(cls));
            }
        }

        return Scene.withAutoOpen(parentRuntime, orderedUnmodifiableCopy(companionRuntimes), Map.copyOf(lazyFactories),
                Map.copyOf(preActivated), composition,
                new Scene.AutoOpen(this.contractClass, routePattern));
    }

    /**
     * Instantiate companion contracts declared by the Layout.
     */
    private Map<Class<? extends ViewContract>, ContractRuntime> instantiateCompanions(ComponentContext context) {
        Set<Class<? extends ViewContract>> requiredByLayout = layout.requiredContracts();
        Group contracts = composition.contracts();
        Map<Class<? extends ViewContract>, ContractRuntime> companions = new LinkedHashMap<>();

        for (Class<? extends ViewContract> cls : contracts.contractClasses()) {
            if (requiredByLayout.contains(cls)) {
                Function<Lookup, ViewContract> factory = contracts.contractFactory(cls);
                ContractRuntime companion = instantiateContract(cls, factory, context);
                if (companion != null) {
                    companions.put(cls, companion);
                }
            }
        }

        return companions;
    }

    private static <K, V> Map<K, V> orderedUnmodifiableCopy(Map<K, V> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    /**
     * Collect lazy factories for all contracts that are not the routed contract or companions.
     */
    private Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> collectLazyFactories(
            Class<? extends ViewContract> routedClass,
            Map<Class<? extends ViewContract>, ContractRuntime> companionRuntimes) {
        Group contracts = composition.contracts();
        Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> lazyFactories = new HashMap<>();
        for (Class<? extends ViewContract> cls : contracts.contractClasses()) {
            if (!cls.equals(routedClass) && !companionRuntimes.containsKey(cls)) {
                lazyFactories.put(cls, contracts.contractFactory(cls));
            }
        }
        return lazyFactories;
    }

    /**
     * Instantiate a contract from its factory.
     */
    ContractRuntime instantiateContract(Class<? extends ViewContract> contractClass,
                                        Function<Lookup, ViewContract> factory,
                                        ComponentContext context) {
        return ContractRuntime.instantiate(contractClass, factory, context);
    }

    private void startServicesLifecycleHandlers(ComponentContext context) {
        Services services = composition.services();
        if (services == null) return;
        Lookup lookup = LookupFactory.create(context);
        for (Object service : services.asMap().values()) {
            if (service instanceof ServicesLifecycleHandler handler) {
                handler.onStart(lookup);
            }
        }
    }
}
