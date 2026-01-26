package rsp.compositions.composition;

import rsp.component.Lookup;
import rsp.compositions.contract.ViewContract;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * ViewsPlacements - Fluent builder for view placements.
 * <p>
 * Provides a convenient API for declaring slot-based view placements:
 * <pre>
 * new ViewsPlacements()
 *     .place(Slot.PRIMARY, PostsListContract.class, PostsListContract::new)
 *     .place(Slot.OVERLAY, PostEditContract.class, PostEditContract::new)
 * </pre>
 */
public class ViewsPlacements {
    private final List<ViewPlacement> placements = new ArrayList<>();

    /**
     * Add a view placement.
     *
     * @param slot The UI slot (PRIMARY, OVERLAY, SECONDARY)
     * @param contractClass The contract class for routing and resolution
     * @param factory Factory function that takes Lookup and produces a ViewContract
     * @return this builder for chaining
     */
    public ViewsPlacements place(Slot slot,
                                  Class<? extends ViewContract> contractClass,
                                  Function<Lookup, ViewContract> factory) {
        placements.add(new ViewPlacement(slot, contractClass, factory));
        return this;
    }

    /**
     * Returns an immutable copy of the placements list.
     *
     * @return immutable list of ViewPlacements
     */
    public List<ViewPlacement> toList() {
        return List.copyOf(placements);
    }
}
