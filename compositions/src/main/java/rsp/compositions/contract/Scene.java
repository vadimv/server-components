package rsp.compositions.contract;

import rsp.component.Lookup;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;

import java.util.*;
import java.util.function.Function;

/**
 * Scene - Immutable snapshot of a rendered view's complete contract setup.
 * <p>
 * A Scene is always valid — it represents a successfully built, authorized view.
 * Error and authorization failures are handled via exceptions during scene building.
 * <p>
 * A Scene represents everything needed to render a view:
 * <ul>
 *   <li>Routed contract instance (matched by Router, nullable for IDE-style UIs)</li>
 *   <li>Companion contracts (eagerly instantiated, requested by Layout)</li>
 *   <li>Lazy factories (for on-demand instantiation via SHOW events)</li>
 *   <li>Pre-activated contracts (for auto-open when overlay-like contracts are URL-routed)</li>
 *   <li>Composition reference (Contracts is derived from composition)</li>
 *   <li>Build metadata (timestamp)</li>
 *   <li>Auto-open info (when overlay-like contract is routed directly)</li>
 *   <li>Page title for HTML title tag</li>
 * </ul>
 *
 * @param routedRuntime The contract runtime matched by the Router (nullable for IDE-style UIs)
 * @param companionRuntimes Eagerly instantiated contract runtimes requested by the Layout
 * @param lazyFactories Factories for on-demand instantiation via SHOW events
 * @param preActivatedRuntimes Pre-instantiated runtimes for LayerComponent auto-open (URL-routed overlays)
 * @param composition The Composition containing the contract (Contracts is derived from here)
 * @param timestamp When the scene was built (for debugging/caching)
 * @param autoOpen Auto-open info for URL-routed overlay contracts (nullable)
 * @param pageTitle The page title for the HTML title tag
 */
public record Scene(ContractRuntime routedRuntime,
                    Map<Class<? extends ViewContract>, ContractRuntime> companionRuntimes,
                    Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> lazyFactories,
                    Map<Class<? extends ViewContract>, ContractRuntime> preActivatedRuntimes,
                    Composition composition,
                    long timestamp,
                    AutoOpen autoOpen,
                    String pageTitle) {
    public Scene {
        Objects.requireNonNull(companionRuntimes, "companionRuntimes");
        Objects.requireNonNull(lazyFactories, "lazyFactories");
        Objects.requireNonNull(preActivatedRuntimes, "preActivatedRuntimes");
        Objects.requireNonNull(composition, "composition");
        Objects.requireNonNull(pageTitle, "pageTitle");
    }

    /**
     * Auto-open info for overlay-like contracts routed directly via URL.
     *
     * @param contractClass The contract class that was auto-activated
     * @param routePattern The route pattern for URL restoration on close (e.g., "/posts/:id")
     */
    public record AutoOpen(Class<? extends ViewContract> contractClass, String routePattern) {
        public AutoOpen {
            Objects.requireNonNull(contractClass, "contractClass");
            Objects.requireNonNull(routePattern, "routePattern");
        }
    }

    /**
     * Returns the Group for this scene, derived from the composition.
     */
    public Group contracts() {
        return composition.contracts();
    }

    /**
     * The contract matched by the Router, preserving the pre-runtime Scene API.
     */
    public ViewContract routedContract() {
        return routedRuntime != null ? routedRuntime.contract() : null;
    }

    /**
     * Get a companion contract by its class.
     *
     * @param contractClass The contract class
     * @return The companion contract, or null if not present
     */
    public ViewContract companionContract(Class<? extends ViewContract> contractClass) {
        final ContractRuntime runtime = companionRuntime(contractClass);
        return runtime != null ? runtime.contract() : null;
    }

    public ContractRuntime companionRuntime(Class<? extends ViewContract> contractClass) {
        return companionRuntimes.get(contractClass);
    }

    /**
     * Companion contracts, preserving the pre-runtime Scene API.
     */
    public Map<Class<? extends ViewContract>, ViewContract> companionContracts() {
        return contractMap(companionRuntimes);
    }

    /**
     * Pre-activated contracts, preserving the pre-runtime Scene API.
     */
    public Map<Class<? extends ViewContract>, ViewContract> preActivatedContracts() {
        return contractMap(preActivatedRuntimes);
    }

    /**
     * Check if this scene has pre-activated contracts for LayerComponent auto-open.
     */
    public boolean hasPreActivatedContracts() {
        return !preActivatedRuntimes.isEmpty();
    }

    /**
     * Get factory for a contract class (lazy/on-demand contracts).
     *
     * @param contractClass The contract class
     * @return The factory, or null if not found
     */
    public Function<Lookup, ViewContract> getFactory(Class<? extends ViewContract> contractClass) {
        return lazyFactories.get(contractClass);
    }

    /**
     * Find an active contract by its class (searches routed + companions).
     *
     * @param contractClass The contract class to find
     * @return The contract, or null if not active
     */
    public ViewContract findContract(Class<? extends ViewContract> contractClass) {
        final ViewContract routed = routedContract();
        if (routed != null && routed.getClass().equals(contractClass)) {
            return routed;
        }
        return companionContract(contractClass);
    }

    public ContractRuntime findRuntime(Class<? extends ViewContract> contractClass) {
        if (routedRuntime != null && routedRuntime.contractClass().equals(contractClass)) {
            return routedRuntime;
        }
        return companionRuntimes.get(contractClass);
    }

    /**
     * Create a new Scene with a different routed contract.
     *
     * @param runtime The new routed contract runtime
     * @return New Scene with the routed contract replaced
     */
    public Scene withRoutedRuntime(ContractRuntime runtime) {
        return new Scene(runtime, companionRuntimes, lazyFactories, preActivatedRuntimes,
                composition, timestamp, autoOpen, titleOf(runtime));
    }

    /**
     * Create a scene with routed contract, companions, and lazy factories.
     */
    public static Scene of(ContractRuntime routedRuntime,
                           Map<Class<? extends ViewContract>, ContractRuntime> companionRuntimes,
                           Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> lazyFactories,
                           Composition composition) {
        return new Scene(routedRuntime, companionRuntimes, lazyFactories, Map.of(),
                composition, System.currentTimeMillis(), null, titleOf(routedRuntime));
    }

    /**
     * Create a scene with auto-open info (for overlay-like contracts routed via URL).
     *
     * @param routedRuntime The parent routed contract runtime
     * @param companionRuntimes Companion contract runtimes
     * @param lazyFactories Lazy factories
     * @param preActivatedRuntimes Pre-instantiated overlay runtimes for LayerComponent
     * @param composition The composition
     * @param autoOpen Auto-open metadata
     */
    public static Scene withAutoOpen(ContractRuntime routedRuntime,
                                     Map<Class<? extends ViewContract>, ContractRuntime> companionRuntimes,
                                     Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> lazyFactories,
                                     Map<Class<? extends ViewContract>, ContractRuntime> preActivatedRuntimes,
                                     Composition composition,
                                     AutoOpen autoOpen) {
        // Use auto-opened contract's title if available
        String title = titleOf(routedRuntime);
        if (autoOpen != null && preActivatedRuntimes.containsKey(autoOpen.contractClass())) {
            ContractRuntime autoOpenRuntime = preActivatedRuntimes.get(autoOpen.contractClass());
            String autoOpenTitle = titleOf(autoOpenRuntime);
            if (!"App".equals(autoOpenTitle)) {
                title = autoOpenTitle;
            }
        }
        return new Scene(routedRuntime, companionRuntimes, lazyFactories, preActivatedRuntimes,
                composition, System.currentTimeMillis(), autoOpen, title);
    }

    private static Map<Class<? extends ViewContract>, ViewContract> contractMap(
            Map<Class<? extends ViewContract>, ContractRuntime> runtimes) {
        Map<Class<? extends ViewContract>, ViewContract> contracts = new LinkedHashMap<>();
        for (var entry : runtimes.entrySet()) {
            contracts.put(entry.getKey(), entry.getValue().contract());
        }
        return Collections.unmodifiableMap(contracts);
    }

    private static String titleOf(ContractRuntime runtime) {
        if (runtime == null) {
            return "App";
        }
        String title = runtime.contract().title();
        return title != null ? title : "App";
    }
}
