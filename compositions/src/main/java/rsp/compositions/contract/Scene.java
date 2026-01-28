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
 * A Scene represents everything needed to render a view:
 * <ul>
 *   <li>Primary contract instance (fully instantiated, authorized, handlers registered)</li>
 *   <li>Composition reference</li>
 *   <li>Non-primary factories (for on-demand instantiation via SHOW events)</li>
 *   <li>Active contracts by slot (currently shown contracts)</li>
 *   <li>UiRegistry for resolving contracts to UI components</li>
 *   <li>Authorization state</li>
 *   <li>Build metadata (timestamp, any errors)</li>
 *   <li>Auto-open contract (when non-primary contract is routed directly)</li>
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
 * @param authorized Whether user is authorized for this contract
 * @param timestamp When the scene was built (for debugging/caching)
 * @param error If scene building failed, this contains the exception (other fields may be null)
 * @param autoOpenContract Contract to auto-activate (when non-primary contract routed via URL), null otherwise
 * @param autoOpenRoutePattern The route pattern for auto-opened contracts (for restoring URL on close), null otherwise
 */
public record Scene(
    ViewContract primaryContract,
    Composition composition,
    Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> nonPrimaryFactories,
    Map<Slot, List<ActiveContract>> activeContractsBySlot,
    UiRegistry uiRegistry,
    boolean authorized,
    long timestamp,
    Exception error,
    Class<? extends ViewContract> autoOpenContract,
    String autoOpenRoutePattern
) {
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
     * Check if the scene is valid and ready for rendering.
     *
     * @return true if no error occurred, contract exists, and user is authorized
     */
    public boolean isValid() {
        return error == null && primaryContract != null && authorized;
    }

    /**
     * Get non-primary contracts map.
     * Returns all active contracts from non-PRIMARY slots as a map keyed by class.
     *
     * @return Map of non-primary contracts
     */
    public Map<Class<? extends ViewContract>, ViewContract> nonPrimaryContracts() {
        Map<Class<? extends ViewContract>, ViewContract> result = new HashMap<>();
        for (Map.Entry<Slot, List<ActiveContract>> entry : activeContractsBySlot.entrySet()) {
            if (entry.getKey() != Slot.PRIMARY) {
                for (ActiveContract active : entry.getValue()) {
                    result.put(active.contractClass(), active.contract());
                }
            }
        }
        return result;
    }


    /**
     * Check if this scene has any active non-primary contracts.
     *
     * @return true if there are active non-primary contracts
     */
    public boolean hasNonPrimaryContracts() {
        for (Map.Entry<Slot, List<ActiveContract>> entry : activeContractsBySlot.entrySet()) {
            if (entry.getKey() != Slot.PRIMARY && !entry.getValue().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @deprecated Use {@link #hasNonPrimaryContracts()} instead. This method assumes OVERLAY slot.
     */
    @Deprecated
    public boolean hasOverlays() {
        return hasNonPrimaryContracts();
    }

    /**
     * Get factory for a contract class.
     *
     * @param contractClass The contract class
     * @return The factory, or null if not found
     */
    public Function<Lookup, ViewContract> getFactory(Class<? extends ViewContract> contractClass) {
        return nonPrimaryFactories != null ? nonPrimaryFactories.get(contractClass) : null;
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
            Map.copyOf(newMap), uiRegistry, authorized, timestamp, error,
            autoOpenContract, autoOpenRoutePattern);
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
            Map.copyOf(newMap), uiRegistry, authorized, timestamp, error,
            autoOpenContract, autoOpenRoutePattern);
    }

    /**
     * Create a new Scene with all contracts in a slot cleared.
     *
     * @param slot The slot to clear
     * @return New Scene with slot cleared
     */
    public Scene withSlotCleared(Slot slot) {
        Map<Slot, List<ActiveContract>> newMap = new HashMap<>(activeContractsBySlot);
        newMap.remove(slot);
        return new Scene(primaryContract, composition, nonPrimaryFactories,
            Map.copyOf(newMap), uiRegistry, authorized, timestamp, error,
            autoOpenContract, autoOpenRoutePattern);
    }

    // ===== Factory Methods =====

    /**
     * Create a valid scene with primary contract, composition, and UI registry (no non-primary contracts).
     */
    public static Scene of(ViewContract primaryContract, Composition composition, UiRegistry uiRegistry) {
        return new Scene(primaryContract, composition, Map.of(), Map.of(), uiRegistry,
            true, System.currentTimeMillis(), null, null, null);
    }

    /**
     * Create a valid scene with primary contract, composition, non-primary factories, and UI registry.
     */
    public static Scene of(ViewContract primaryContract, Composition composition,
                           Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> nonPrimaryFactories,
                           UiRegistry uiRegistry) {
        return new Scene(primaryContract, composition,
            nonPrimaryFactories != null ? nonPrimaryFactories : Map.of(),
            Map.of(), uiRegistry, true, System.currentTimeMillis(), null, null, null);
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
        if (activeContracts != null && !activeContracts.isEmpty()) {
            // Group by slot - don't assume OVERLAY
            for (Map.Entry<Class<? extends ViewContract>, ViewContract> entry : activeContracts.entrySet()) {
                // Find slot from composition
                ViewPlacement placement = composition.placementFor(entry.getKey());
                Slot slot = placement != null ? placement.slot() : Slot.OVERLAY; // Fallback to OVERLAY for backward compat

                List<ActiveContract> slotActives = activeBySlot.computeIfAbsent(slot, k -> new ArrayList<>());
                slotActives.add(new ActiveContract(entry.getValue(), entry.getKey(), Map.of()));
            }
            // Make immutable
            Map<Slot, List<ActiveContract>> immutable = new HashMap<>();
            for (Map.Entry<Slot, List<ActiveContract>> entry : activeBySlot.entrySet()) {
                immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            activeBySlot = Map.copyOf(immutable);
        }
        return new Scene(primaryContract, composition,
            nonPrimaryFactories != null ? nonPrimaryFactories : Map.of(),
            activeBySlot, uiRegistry, true, System.currentTimeMillis(), null,
            autoOpenContract, autoOpenRoutePattern);
    }


    /**
     * Create an unauthorized scene.
     */
    public static Scene unauthorized(ViewContract contract, Composition composition, UiRegistry uiRegistry) {
        return new Scene(contract, composition, Map.of(), Map.of(), uiRegistry,
            false, System.currentTimeMillis(), null, null, null);
    }

    /**
     * Create an error scene.
     */
    public static Scene error(Exception e) {
        return new Scene(null, null, Map.of(), Map.of(), null,
            false, System.currentTimeMillis(), e, null, null);
    }

}
