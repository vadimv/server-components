package rsp.compositions.composition;

import rsp.component.Lookup;
import rsp.compositions.contract.ViewContract;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * ViewsPlacements - Fluent builder for view placements.
 * <p>
 * Provides a convenient API for declaring view placements:
 * <pre>
 * new ViewsPlacements()
 *     .place(PostsListContract.class, PostsListContract::new)
 *     .place(PostEditContract.class, PostEditContract::new)
 * </pre>
 * <p>
 * Lifecycle (eager vs lazy) is derived from Router + Layout configuration.
 */
public class ViewsPlacements {
    private final List<ViewPlacement> placements = new ArrayList<>();

    /**
     * Add a view placement.
     *
     * @param contractClass The contract class for routing and resolution
     * @param factory Factory function that takes Lookup and produces a ViewContract
     * @return this builder for chaining
     */
    public ViewsPlacements place(Class<? extends ViewContract> contractClass,
                                  Function<Lookup, ViewContract> factory) {
        placements.add(new ViewPlacement(contractClass, factory));
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
