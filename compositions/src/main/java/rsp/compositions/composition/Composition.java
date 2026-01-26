package rsp.compositions.composition;

import rsp.compositions.contract.ViewContract;
import rsp.compositions.routing.Router;

import java.util.List;
import java.util.Objects;

/**
 * Composition - Declares a feature domain's view placements and routes.
 * <p>
 * Each composition groups related views by declaring their slots, contract factories, and routes.
 * The Slot determines rendering behavior:
 * <ul>
 *   <li>{@link Slot#PRIMARY} - Full page content, navigated via Router URLs</li>
 *   <li>{@link Slot#OVERLAY} - Popup/modal, component state only (no URL change)</li>
 *   <li>{@link Slot#SECONDARY} - Split view (reserved for future use)</li>
 * </ul>
 * <p>
 * Route resolution iterates Compositions in order - the first matching route wins.
 * <p>
 * Action handling is delegated to Contracts (e.g., EditViewContract.save(), delete()).
 */
public class Composition {
    private final Router router;
    private final List<ViewPlacement> views;

    /**
     * Create a Composition with its router and view placements.
     *
     * @param router The router for this composition's routes
     * @param placements The view placements builder
     */
    public Composition(Router router, ViewsPlacements placements) {
        Objects.requireNonNull(router, "router cannot be null");
        Objects.requireNonNull(placements, "placements cannot be null");
        this.router = router;
        this.views = placements.toList();
    }

    /**
     * The router for this composition's routes.
     *
     * @return the Router instance
     */
    public Router router() {
        return router;
    }

    /**
     * View placements for this composition.
     * Each placement declares a Slot and a contract factory.
     *
     * @return immutable list of ViewPlacements
     */
    public List<ViewPlacement> views() {
        return views;
    }

    /**
     * Find all placements with the given slot.
     *
     * @param slot The slot to filter by
     * @return List of ViewPlacements with that slot (may be empty)
     */
    public List<ViewPlacement> placementsForSlot(Slot slot) {
        return views.stream()
                .filter(p -> p.slot() == slot)
                .toList();
    }

    /**
     * Find the placement for a specific contract class.
     *
     * @param contractClass The contract class to find
     * @return The ViewPlacement, or null if not found
     */
    public ViewPlacement placementFor(Class<? extends ViewContract> contractClass) {
        return views.stream()
                .filter(p -> p.contractClass().equals(contractClass))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get the slot type for a specific contract class.
     *
     * @param contractClass The contract class to look up
     * @return The Slot, or null if contract not found in this composition
     */
    public Slot slotFor(Class<? extends ViewContract> contractClass) {
        ViewPlacement placement = placementFor(contractClass);
        return placement != null ? placement.slot() : null;
    }

    /**
     * Find the first PRIMARY slot placement in this composition.
     * Useful for finding the "parent" primary when an OVERLAY is routed directly.
     *
     * @return The first PRIMARY ViewPlacement, or null if none
     */
    public ViewPlacement primaryPlacement() {
        return views.stream()
                .filter(p -> p.slot() == Slot.PRIMARY)
                .findFirst()
                .orElse(null);
    }
}
