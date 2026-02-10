package rsp.compositions.layout;

import rsp.component.Lookup;
import rsp.compositions.contract.Scene;
import rsp.dsl.Definition;

/**
 * Strategy interface for resolving and rendering Scene content in a visual layout.
 * <p>
 * Each implementation owns the full pipeline: extracting contracts from Scene,
 * resolving them to UI components, and rendering the visual structure.
 * <p>
 * Examples: sidebar layout, dashboard grid, map view.
 *
 * @see DefaultLayout
 */
public interface Layout {
    /**
     * Resolve and render the scene content.
     *
     * @param scene  the scene containing contracts, UiRegistry, and layout data
     * @param lookup for event publishing (e.g., overlay close)
     * @return the rendered layout definition
     */
    Definition resolve(Scene scene, Lookup lookup);
}
