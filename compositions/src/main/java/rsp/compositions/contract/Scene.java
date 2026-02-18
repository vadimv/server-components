package rsp.compositions.contract;

import rsp.component.Lookup;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.UiRegistry;

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
 *   <li>Composition reference</li>
 *   <li>UiRegistry for resolving contracts to UI components</li>
 *   <li>Build metadata (timestamp)</li>
 *   <li>Auto-open info (when overlay-like contract is routed directly)</li>
 *   <li>Page title for HTML title tag</li>
 * </ul>
 *
 * @param routedContract The contract matched by the Router (nullable for IDE-style UIs)
 * @param companionContracts Eagerly instantiated contracts requested by the Layout
 * @param lazyFactories Factories for on-demand instantiation via SHOW events
 * @param preActivatedContracts Pre-instantiated contracts for LayerComponent auto-open (URL-routed overlays)
 * @param composition The Composition containing the contract
 * @param uiRegistry Registry for resolving contracts to UI components
 * @param timestamp When the scene was built (for debugging/caching)
 * @param autoOpen Auto-open info for URL-routed overlay contracts (nullable)
 * @param pageTitle The page title for the HTML title tag
 */
public record Scene(ViewContract routedContract,
                    Map<Class<? extends ViewContract>, ViewContract> companionContracts,
                    Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> lazyFactories,
                    Map<Class<? extends ViewContract>, ViewContract> preActivatedContracts,
                    Composition composition,
                    UiRegistry uiRegistry,
                    long timestamp,
                    AutoOpen autoOpen,
                    String pageTitle) {
    public Scene {
        Objects.requireNonNull(companionContracts, "companionContracts");
        Objects.requireNonNull(lazyFactories, "lazyFactories");
        Objects.requireNonNull(preActivatedContracts, "preActivatedContracts");
        Objects.requireNonNull(composition, "composition");
        Objects.requireNonNull(uiRegistry, "uiRegistry");
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
     * Get a companion contract by its class.
     *
     * @param contractClass The contract class
     * @return The companion contract, or null if not present
     */
    public ViewContract companionContract(Class<? extends ViewContract> contractClass) {
        return companionContracts.get(contractClass);
    }

    /**
     * Check if this scene has pre-activated contracts for LayerComponent auto-open.
     */
    public boolean hasPreActivatedContracts() {
        return !preActivatedContracts.isEmpty();
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
        if (routedContract != null && routedContract.getClass().equals(contractClass)) {
            return routedContract;
        }
        return companionContracts.get(contractClass);
    }

    /**
     * Create a new Scene with a different routed contract.
     *
     * @param contract The new routed contract
     * @return New Scene with the routed contract replaced
     */
    public Scene withRoutedContract(ViewContract contract) {
        return new Scene(contract, companionContracts, lazyFactories, preActivatedContracts,
                composition, uiRegistry, timestamp, autoOpen, titleOf(contract));
    }

    /**
     * Create a scene with routed contract, companions, lazy factories, and UI registry.
     */
    public static Scene of(ViewContract routedContract,
                           Map<Class<? extends ViewContract>, ViewContract> companionContracts,
                           Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> lazyFactories,
                           Composition composition,
                           UiRegistry uiRegistry) {
        return new Scene(routedContract, companionContracts, lazyFactories, Map.of(),
                composition, uiRegistry, System.currentTimeMillis(), null, titleOf(routedContract));
    }

    /**
     * Create a simple scene with routed contract only (no companions or lazy factories).
     */
    public static Scene of(ViewContract routedContract, Composition composition, UiRegistry uiRegistry) {
        return new Scene(routedContract, Map.of(), Map.of(), Map.of(),
                composition, uiRegistry, System.currentTimeMillis(), null, titleOf(routedContract));
    }

    /**
     * Create a scene with auto-open info (for overlay-like contracts routed via URL).
     *
     * @param routedContract The parent routed contract
     * @param companionContracts Companion contracts
     * @param lazyFactories Lazy factories
     * @param preActivatedContracts Pre-instantiated overlay contracts for LayerComponent
     * @param composition The composition
     * @param uiRegistry The UI registry
     * @param autoOpen Auto-open metadata
     */
    public static Scene withAutoOpen(ViewContract routedContract,
                                     Map<Class<? extends ViewContract>, ViewContract> companionContracts,
                                     Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> lazyFactories,
                                     Map<Class<? extends ViewContract>, ViewContract> preActivatedContracts,
                                     Composition composition,
                                     UiRegistry uiRegistry,
                                     AutoOpen autoOpen) {
        // Use auto-opened contract's title if available
        String title = titleOf(routedContract);
        if (autoOpen != null && preActivatedContracts.containsKey(autoOpen.contractClass())) {
            ViewContract autoOpenContract = preActivatedContracts.get(autoOpen.contractClass());
            String autoOpenTitle = titleOf(autoOpenContract);
            if (!"App".equals(autoOpenTitle)) {
                title = autoOpenTitle;
            }
        }
        return new Scene(routedContract, companionContracts, lazyFactories, preActivatedContracts,
                composition, uiRegistry, System.currentTimeMillis(), autoOpen, title);
    }

    private static String titleOf(ViewContract contract) {
        if (contract == null) {
            return "App";
        }
        String title = contract.title();
        return title != null ? title : "App";
    }
}
