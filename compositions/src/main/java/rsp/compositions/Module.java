package rsp.compositions;

import java.util.List;

/**
 * Module - Declares a feature domain's view placements.
 * <p>
 * Each module groups related views by declaring their slots and contract factories.
 * The Slot determines rendering behavior:
 * <ul>
 *   <li>{@link Slot#PRIMARY} - Full page content, navigated via Router URLs</li>
 *   <li>{@link Slot#OVERLAY} - Popup/modal, component state only (no URL change)</li>
 *   <li>{@link Slot#SECONDARY} - Split view (reserved for future use)</li>
 * </ul>
 * <p>
 * Module is a pure declaration - no behavior configuration.
 * Action handling is delegated to Contracts (e.g., EditViewContract.save(), delete()).
 */
public interface Module {
    /**
     * View placements for this module.
     * Each placement declares a Slot and a contract factory.
     */
    List<ViewPlacement> views();

    /**
     * Find all placements with the given slot.
     *
     * @param slot The slot to filter by
     * @return List of ViewPlacements with that slot (may be empty)
     */
    default List<ViewPlacement> placementsForSlot(Slot slot) {
        return views().stream()
                .filter(p -> p.slot() == slot)
                .toList();
    }

    /**
     * Find the placement for a specific contract class.
     *
     * @param contractClass The contract class to find
     * @return The ViewPlacement, or null if not found
     */
    default ViewPlacement placementFor(Class<? extends ViewContract> contractClass) {
        return views().stream()
                .filter(p -> p.contractClass().equals(contractClass))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get the slot type for a specific contract class.
     *
     * @param contractClass The contract class to look up
     * @return The Slot, or null if contract not found in this module
     */
    default Slot slotFor(Class<? extends ViewContract> contractClass) {
        ViewPlacement placement = placementFor(contractClass);
        return placement != null ? placement.slot() : null;
    }

    /**
     * Find the first PRIMARY slot placement in this module.
     * Useful for finding the "parent" primary when an OVERLAY is routed directly.
     *
     * @return The first PRIMARY ViewPlacement, or null if none
     */
    default ViewPlacement primaryPlacement() {
        return views().stream()
                .filter(p -> p.slot() == Slot.PRIMARY)
                .findFirst()
                .orElse(null);
    }
}
