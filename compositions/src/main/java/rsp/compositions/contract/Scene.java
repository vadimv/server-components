package rsp.compositions.contract;

import rsp.component.Lookup;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Slot;
import rsp.compositions.composition.UiRegistry;
import rsp.compositions.composition.ViewPlacement;

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
 *   <li>Primary contract instance (fully instantiated, authorized, handlers registered)</li>
 *   <li>Composition reference</li>
 *   <li>Non-primary factories (for on-demand instantiation via SHOW events)</li>
 *   <li>Active contracts by slot (currently shown contracts)</li>
 *   <li>UiRegistry for resolving contracts to UI components</li>
 *   <li>Build metadata (timestamp)</li>
 *   <li>Auto-open contract (when non-primary contract is routed directly)</li>
 *   <li>Page title for HTML title tag</li>
 * </ul>
 * <p>
 * On-demand instantiation:
 * <ul>
 *   <li>Non-primary contracts are NOT pre-instantiated at mount</li>
 *   <li>Factories are stored for lazy instantiation on SHOW events</li>
 *   <li>SceneComponent handles SHOW/HIDE to manage active contracts</li>
 *   <li>Supports multiple active contracts per slot (e.g., nested non-primary contracts)</li>
 * </ul>
 * <p>
 * Slot-agnostic: Scene doesn't know about specific slot types (OVERLAY, SECONDARY, etc.).
 * It only distinguishes PRIMARY vs non-PRIMARY. Layout decides how to render each slot.
 *
 * @param primaryContract The main ViewContract for this route (fully instantiated)
 * @param composition The Composition containing the contract
 * @param nonPrimaryFactories Factories for lazy instantiation of non-primary contracts
 * @param activeContractsBySlot Currently active contracts organized by slot
 * @param uiRegistry Registry for resolving contracts to UI components
 * @param timestamp When the scene was built (for debugging/caching)
 * @param autoOpenContract Contract to auto-activate (when non-primary contract routed via URL), null otherwise
 * @param autoOpenRoutePattern The route pattern for auto-opened contracts (for restoring URL on close), null otherwise
 * @param pageTitle The page title for the HTML title tag (derived from primary contract)
 */
public record Scene(
    ViewContract primaryContract,
    Composition composition,
    Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> nonPrimaryFactories,
    Map<Slot, List<ActiveContract>> activeContractsBySlot,
    UiRegistry uiRegistry,
    long timestamp,
    Class<? extends ViewContract> autoOpenContract,
    String autoOpenRoutePattern,
    String pageTitle
) {
    public Scene {
        Objects.requireNonNull(primaryContract, "primaryContract");
        Objects.requireNonNull(composition, "composition");
        Objects.requireNonNull(nonPrimaryFactories, "nonPrimaryFactories");
        Objects.requireNonNull(activeContractsBySlot, "activeContractsBySlot");
        Objects.requireNonNull(uiRegistry, "uiRegistry");
    }

    /**
     * Helper record for tracking active contracts with their metadata.
     *
     * @param contract The instantiated contract
     * @param contractClass The contract class (for lookup and HIDE matching)
     * @param showData Data passed with the SHOW event
     */
    public record ActiveContract(
        ViewContract contract,
        Class<? extends ViewContract> contractClass,
        Map<String, Object> showData
    ) {}

    /**
     * Get the LEFT_SIDEBAR contract if present.
     *
     * @return The LEFT_SIDEBAR contract, or null if not present
     */
    public ViewContract leftSidebarContract() {
        List<ActiveContract> sidebarContracts = activeContractsBySlot.get(Slot.LEFT_SIDEBAR);
        if (sidebarContracts == null || sidebarContracts.isEmpty()) {
            return null;
        }
        // Only one LEFT_SIDEBAR contract is expected
        return sidebarContracts.get(0).contract();
    }

    /**
     * Get the RIGHT_SIDEBAR contract if present.
     *
     * @return The RIGHT_SIDEBAR contract, or null if not present
     */
    public ViewContract rightSidebarContract() {
        List<ActiveContract> sidebarContracts = activeContractsBySlot.get(Slot.RIGHT_SIDEBAR);
        if (sidebarContracts == null || sidebarContracts.isEmpty()) {
            return null;
        }
        return sidebarContracts.get(0).contract();
    }

    /**
     * Get overlay contracts map (excludes PRIMARY, LEFT_SIDEBAR, and RIGHT_SIDEBAR).
     * Returns active contracts from OVERLAY and similar slots as a map keyed by class.
     *
     * @return Map of overlay contracts
     */
    public Map<Class<? extends ViewContract>, ViewContract> nonPrimaryContracts() {
        Map<Class<? extends ViewContract>, ViewContract> result = new HashMap<>();
        for (Map.Entry<Slot, List<ActiveContract>> entry : activeContractsBySlot.entrySet()) {
            if (entry.getKey() != Slot.PRIMARY && entry.getKey() != Slot.LEFT_SIDEBAR
                    && entry.getKey() != Slot.RIGHT_SIDEBAR) {
                for (ActiveContract active : entry.getValue()) {
                    result.put(active.contractClass(), active.contract());
                }
            }
        }
        return result;
    }


    /**
     * Check if this scene has any active overlay contracts (excludes PRIMARY and sidebars).
     *
     * @return true if there are active overlay contracts
     */
    public boolean hasNonPrimaryContracts() {
        for (Map.Entry<Slot, List<ActiveContract>> entry : activeContractsBySlot.entrySet()) {
            if (entry.getKey() != Slot.PRIMARY && entry.getKey() != Slot.LEFT_SIDEBAR
                    && entry.getKey() != Slot.RIGHT_SIDEBAR
                    && !entry.getValue().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get factory for a contract class.
     *
     * @param contractClass The contract class
     * @return The factory, or null if not found
     */
    public Function<Lookup, ViewContract> getFactory(Class<? extends ViewContract> contractClass) {
        return nonPrimaryFactories.get(contractClass);
    }

    /**
     * Get all active contracts for a slot.
     *
     * @param slot The slot type
     * @return List of active contracts (empty if none)
     */
    public List<ViewContract> contractsForSlot(Slot slot) {
        List<ActiveContract> actives = activeContractsBySlot.get(slot);
        if (actives == null || actives.isEmpty()) {
            return List.of();
        }
        return actives.stream().map(ActiveContract::contract).toList();
    }

    /**
     * Check if slot has any active contracts.
     *
     * @param slot The slot type
     * @return true if slot has active contracts
     */
    public boolean hasActiveContracts(Slot slot) {
        List<ActiveContract> actives = activeContractsBySlot.get(slot);
        return actives != null && !actives.isEmpty();
    }

    /**
     * Find an active contract by its class.
     *
     * @param contractClass The contract class to find
     * @return The contract, or null if not active
     */
    public ViewContract findContract(Class<? extends ViewContract> contractClass) {
        for (List<ActiveContract> actives : activeContractsBySlot.values()) {
            for (ActiveContract active : actives) {
                if (active.contractClass().equals(contractClass)) {
                    return active.contract();
                }
            }
        }
        return null;
    }

    /**
     * Create a new Scene with an active contract added to a slot.
     *
     * @param slot The target slot
     * @param contract The contract instance
     * @param contractClass The contract class
     * @param showData Data passed with SHOW event
     * @return New Scene with contract added
     */
    public Scene withActiveContract(Slot slot, ViewContract contract,
                                    Class<? extends ViewContract> contractClass,
                                    Map<String, Object> showData) {
        Map<Slot, List<ActiveContract>> newMap = new HashMap<>(activeContractsBySlot);
        List<ActiveContract> slotContracts = new ArrayList<>(
            newMap.getOrDefault(slot, List.of()));
        slotContracts.add(new ActiveContract(contract, contractClass, showData));
        newMap.put(slot, List.copyOf(slotContracts));
        return new Scene(primaryContract, composition, nonPrimaryFactories,
            Map.copyOf(newMap), uiRegistry, timestamp,
            autoOpenContract, autoOpenRoutePattern, pageTitle);
    }

    public Scene withPrimaryContract(ViewContract contract) {
        return new Scene(contract, composition, nonPrimaryFactories,
                activeContractsBySlot, uiRegistry, timestamp,
                autoOpenContract, autoOpenRoutePattern, pageTitle);
    }

    /**
     * Create a new Scene with a contract removed (closed).
     *
     * @param contractClass The contract class to remove
     * @return New Scene with contract removed
     */
    public Scene withContractClosed(Class<? extends ViewContract> contractClass) {
        Map<Slot, List<ActiveContract>> newMap = new HashMap<>();
        for (Map.Entry<Slot, List<ActiveContract>> entry : activeContractsBySlot.entrySet()) {
            List<ActiveContract> filtered = entry.getValue().stream()
                .filter(ac -> !ac.contractClass().equals(contractClass))
                .toList();
            if (!filtered.isEmpty()) {
                newMap.put(entry.getKey(), filtered);
            }
        }
        return new Scene(primaryContract, composition, nonPrimaryFactories,
            Map.copyOf(newMap), uiRegistry, timestamp,
            autoOpenContract, autoOpenRoutePattern, pageTitle);
    }


    /**
     * Create a valid scene with primary contract, composition, and UI registry (no non-primary contracts).
     */
    public static Scene of(ViewContract primaryContract, Composition composition, UiRegistry uiRegistry) {
        String title = primaryContract.title() != null ? primaryContract.title() : "App";
        return new Scene(primaryContract, composition, Map.of(), Map.of(), uiRegistry,
            System.currentTimeMillis(), null, null, title);
    }

    /**
     * Create a valid scene with primary contract, composition, non-primary factories, and UI registry.
     */
    public static Scene of(ViewContract primaryContract, Composition composition,
                           Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> nonPrimaryFactories,
                           UiRegistry uiRegistry) {
        String title = primaryContract.title() != null ? primaryContract.title() : "App";
        return new Scene(primaryContract, composition,
            nonPrimaryFactories, Map.of(), uiRegistry,
            System.currentTimeMillis(), null, null, title);
    }

    /**
     * Create a valid scene with primary contract, both sidebar contracts, non-primary factories, and UI registry.
     *
     * @param primaryContract The primary contract
     * @param leftSidebarContract The left sidebar contract (can be null)
     * @param rightSidebarContract The right sidebar contract (can be null)
     * @param composition The composition
     * @param nonPrimaryFactories Factories for lazy instantiation
     * @param uiRegistry The UI registry
     */
    public static Scene withSidebars(ViewContract primaryContract,
                                      ViewContract leftSidebarContract,
                                      ViewContract rightSidebarContract,
                                      Composition composition,
                                      Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> nonPrimaryFactories,
                                      UiRegistry uiRegistry) {
        String title = primaryContract.title() != null ? primaryContract.title() : "App";
        Map<Slot, List<ActiveContract>> activeBySlot = new HashMap<>();

        if (leftSidebarContract != null) {
            activeBySlot.put(Slot.LEFT_SIDEBAR, List.of(
                new ActiveContract(leftSidebarContract, leftSidebarContract.getClass(), Map.of())
            ));
        }
        if (rightSidebarContract != null) {
            activeBySlot.put(Slot.RIGHT_SIDEBAR, List.of(
                new ActiveContract(rightSidebarContract, rightSidebarContract.getClass(), Map.of())
            ));
        }

        return new Scene(primaryContract, composition,
            nonPrimaryFactories,
            Map.copyOf(activeBySlot), uiRegistry,
            System.currentTimeMillis(), null, null, title);
    }

    /**
     * Create a valid scene with auto-open contract (for non-primary contracts routed via URL).
     *
     * @param primaryContract The primary contract (parent route's contract)
     * @param composition The composition
     * @param nonPrimaryFactories Factories for lazy instantiation
     * @param activeContracts Pre-activated non-primary contracts (for auto-open case)
     * @param uiRegistry The UI registry
     * @param autoOpenContract The contract class to auto-activate
     * @param autoOpenRoutePattern The route pattern for URL sync (e.g., "/posts/:id")
     */
    public static Scene withAutoOpenContract(ViewContract primaryContract, Composition composition,
                                             Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> nonPrimaryFactories,
                                             Map<Class<? extends ViewContract>, ViewContract> activeContracts,
                                             UiRegistry uiRegistry,
                                             Class<? extends ViewContract> autoOpenContract,
                                             String autoOpenRoutePattern) {
        Map<Slot, List<ActiveContract>> activeBySlot = new HashMap<>();
        // Determine page title - use auto-opened contract's title if available, otherwise primary's
        String title = "App";
        if (activeContracts != null && !activeContracts.isEmpty()) {
            // Group by slot - don't assume OVERLAY
            for (Map.Entry<Class<? extends ViewContract>, ViewContract> entry : activeContracts.entrySet()) {
                // Find slot from composition
                ViewPlacement placement = composition.placementFor(entry.getKey());
                Slot slot = placement != null ? placement.slot() : Slot.OVERLAY; // Fallback to OVERLAY for backward compat

                List<ActiveContract> slotActives = activeBySlot.computeIfAbsent(slot, k -> new ArrayList<>());
                slotActives.add(new ActiveContract(entry.getValue(), entry.getKey(), Map.of()));

                // Use the auto-opened contract's page title
                if (entry.getKey().equals(autoOpenContract)) {
                    title = entry.getValue().title();
                }
            }
            // Make immutable
            Map<Slot, List<ActiveContract>> immutable = new HashMap<>();
            for (Map.Entry<Slot, List<ActiveContract>> entry : activeBySlot.entrySet()) {
                immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            activeBySlot = Map.copyOf(immutable);
        }
        // Fallback to primary contract's title if auto-open title wasn't found
        if ("App".equals(title)) {
            title = primaryContract.title();
        }
        return new Scene(primaryContract, composition,
            nonPrimaryFactories,
            activeBySlot, uiRegistry,
            System.currentTimeMillis(),
            autoOpenContract, autoOpenRoutePattern, title);
    }
}
