package rsp.compositions.layout;

import rsp.component.Lookup;
import rsp.compositions.contract.Scene;
import rsp.compositions.contract.ViewContract;
import rsp.dsl.Definition;

import java.util.Set;

/**
 * Strategy interface for resolving and rendering Scene content in a visual layout.
 * <p>
 * Each implementation owns the full pipeline: extracting contracts from Scene,
 * resolving them to UI components, and rendering the visual structure.
 * <p>
 * Layouts also declare which non-routed contracts they need eagerly instantiated
 * (companions) via {@link #requiredContracts()}.
 * <p>
 * Examples: sidebar layout, dashboard grid, IDE panel layout.
 *
 * @see DefaultLayout
 */
public interface Layout {
    /**
     * Declare which contracts this layout needs eagerly instantiated (companions).
     * <p>
     * The framework instantiates these alongside the routed contract during scene building.
     * Contracts not listed here and not matched by the Router are stored as lazy factories.
     *
     * @return set of contract classes this layout requires
     */
    default Set<Class<? extends ViewContract>> requiredContracts() {
        return Set.of();
    }

    /**
     * Resolve and render the scene content.
     *
     * @param scene  the scene containing contracts, Contracts, and layout data
     * @param lookup for event publishing (e.g., overlay close)
     * @return the rendered layout definition
     */
    Definition resolve(Scene scene, Lookup lookup);
}
