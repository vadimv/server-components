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
 * All fields are non-null (enforced by compact constructor).
 * <p>
 * A Scene represents everything needed to render a view:
 * <ul>
 *   <li>Primary contract instance (fully instantiated, authorized, handlers registered)</li>
 *   <li>Composition reference</li>
 *   <li>Non-primary factories (for on-demand instantiation via SHOW events)</li>
 *   <li>Active contracts by slot (currently shown contracts)</li>
 *   <li>UiRegistry for resolving contracts to UI components</li>
 *   <li>Build metadata (timestamp)</li>
 *   <li>Auto-open info (when non-primary contract is routed directly)</li>
 *   <li>Page title for HTML title tag</li>
 * </ul>
 *
 * @param primaryContract The main ViewContract for this route (fully instantiated)
 * @param composition The Composition containing the contract
 * @param nonPrimaryFactories Factories for lazy instantiation of non-primary contracts
 * @param activeContractsBySlot Currently active contracts organized by slot
 * @param uiRegistry Registry for resolving contracts to UI components
 * @param timestamp When the scene was built (for debugging/caching)
 * @param autoOpen Auto-open info for non-primary contracts routed via URL, empty if not applicable, can be null
 * @param pageTitle The page title for the HTML title tag (derived from primary contract)
 */
public record Scene(ViewContract primaryContract,
                    Composition composition,
                    Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> nonPrimaryFactories,
                    Map<Slot, List<ActiveContract>> activeContractsBySlot,
                    UiRegistry uiRegistry,
                    long timestamp,
                    AutoOpen autoOpen,
                    String pageTitle) {
    public Scene {
        Objects.requireNonNull(primaryContract, "primaryContract");
        Objects.requireNonNull(composition, "composition");
        Objects.requireNonNull(nonPrimaryFactories, "nonPrimaryFactories");
        Objects.requireNonNull(activeContractsBySlot, "activeContractsBySlot");
        Objects.requireNonNull(uiRegistry, "uiRegistry");
        Objects.requireNonNull(pageTitle, "pageTitle");
    }

    /**
     * Auto-open info for non-primary contracts routed directly via URL.
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
     * Helper record for tracking active contracts with their metadata.
     *
     * @param contract The instantiated contract
     * @param contractClass The contract class (for lookup and HIDE matching)
     * @param showData Data passed with the SHOW event
     */
    public record ActiveContract(ViewContract contract,
                                 Class<? extends ViewContract> contractClass,
                                 Map<String, Object> showData) {
        public ActiveContract {
            Objects.requireNonNull(contract, "contract");
            Objects.requireNonNull(contractClass, "contractClass");
            Objects.requireNonNull(showData, "showData");
            showData = Map.copyOf(showData);
        }
    }

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
        Objects.requireNonNull(showData, "showData");
        Map<Slot, List<ActiveContract>> newMap = new HashMap<>(activeContractsBySlot);
        List<ActiveContract> slotContracts = new ArrayList<>(
            newMap.getOrDefault(slot, List.of()));
        slotContracts.add(new ActiveContract(contract, contractClass, showData));
        newMap.put(slot, List.copyOf(slotContracts));
        return new Scene(primaryContract, composition, nonPrimaryFactories,
            Map.copyOf(newMap), uiRegistry, timestamp, autoOpen, pageTitle);
    }

    public Scene withPrimaryContract(ViewContract contract) {
        return new Scene(contract, composition, nonPrimaryFactories,
                activeContractsBySlot, uiRegistry, timestamp, autoOpen, pageTitle);
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
            Map.copyOf(newMap), uiRegistry, timestamp, autoOpen, pageTitle);
    }


    /**
     * Create a scene with primary contract, composition, and UI registry (no non-primary contracts).
     */
    public static Scene of(ViewContract primaryContract, Composition composition, UiRegistry uiRegistry) {
        return new Scene(primaryContract, composition, Map.of(), Map.of(), uiRegistry,
            System.currentTimeMillis(), null, titleOf(primaryContract));
    }

    /**
     * Create a scene with primary contract, composition, non-primary factories, and UI registry.
     */
    public static Scene of(ViewContract primaryContract, Composition composition,
                           Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> nonPrimaryFactories,
                           UiRegistry uiRegistry) {
        return new Scene(primaryContract, composition,
            nonPrimaryFactories, Map.of(), uiRegistry,
            System.currentTimeMillis(), null, titleOf(primaryContract));
    }

    /**
     * Create a scene with primary contract, both sidebar contracts, non-primary factories, and UI registry.
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
            System.currentTimeMillis(), null, titleOf(primaryContract));
    }

    /**
     * Create a scene with auto-open contract (for non-primary contracts routed via URL).
     *
     * @param primaryContract The primary contract (parent route's contract)
     * @param composition The composition
     * @param nonPrimaryFactories Factories for lazy instantiation
     * @param activeContracts Pre-activated non-primary contracts (for auto-open case)
     * @param uiRegistry The UI registry
     * @param autoOpen The auto-open info (contract class and route pattern)
     */
    public static Scene withAutoOpenContract(ViewContract primaryContract, Composition composition,
                                             Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> nonPrimaryFactories,
                                             Map<Class<? extends ViewContract>, ViewContract> activeContracts,
                                             UiRegistry uiRegistry,
                                             AutoOpen autoOpen) {
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
                if (entry.getKey().equals(autoOpen.contractClass())) {
                    title = titleOf(entry.getValue());
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
            title = titleOf(primaryContract);
        }
        return new Scene(primaryContract, composition,
            nonPrimaryFactories,
            activeBySlot, uiRegistry,
            System.currentTimeMillis(),
            autoOpen, title);
    }

    private static String titleOf(ViewContract contract) {
        if (contract == null) {
            return "App";
        }
        String title = contract.title();
        return title != null ? title : "App";
    }
}
