package rsp.compositions.layout;

import rsp.compositions.block.Block;
import rsp.compositions.block.BlockTarget;

import rsp.component.Lookup;
import rsp.compositions.block.Scene;
import rsp.dsl.Definition;

import java.util.Set;

/**
 * Strategy interface for resolving and rendering Scene content in a visual layout.
 * <p>
 * Each implementation owns the full pipeline: extracting blocks from Scene,
 * resolving them to UI components, and rendering the visual structure.
 * <p>
 * Layouts also declare which non-routed blocks they need eagerly instantiated
 * (companions) via {@link #requiredBlocks()}.
 * <p>
 * Examples: sidebar layout, dashboard grid, IDE panel layout.
 *
 * @see DefaultLayout
 */
public interface Layout {
    /**
     * Declare which blocks this layout needs eagerly instantiated (companions).
     * <p>
     * The framework instantiates these alongside the routed block during scene building.
     * Blocks not listed here and not matched by the Router are stored as lazy factories.
     *
     * @return set of block classes this layout requires
     */
    default Set<Class<? extends Block<?, ?>>> requiredBlocks() {
        return Set.of();
    }

    /** Binding keys required as persistent layout companions. */
    default Set<Object> requiredBlockKeys() {
        return Set.copyOf(requiredBlocks());
    }

    /**
     * Resolve the effective placement for a block shown on demand.
     * <p>
     * The default preserves the historical behavior: {@code SHOW} opens a
     * modal/layer unless a concrete layout overrides this method.
     *
     * @param blockClass the block class being shown
     * @param scene the active scene
     * @return the effective placement decision
     */
    default PlacementDecision resolvePlacement(Class<? extends Block<?, ?>> blockClass,
                                               Scene scene) {
        return PlacementDecision.frameworkDefault();
    }

    /** Resolve placement with both binding identity and Java type available. */
    default PlacementDecision resolvePlacement(BlockTarget target, Scene scene) {
        return resolvePlacement(target.blockClass(), scene);
    }

    /**
     * Resolve and render the scene content.
     *
     * @param scene  the scene containing blocks, Blocks, and layout data
     * @param lookup for event publishing (e.g., overlay close)
     * @return the rendered layout definition
     */
    Definition resolve(Scene scene, Lookup lookup);
}
