package rsp.compositions.composition;

import rsp.compositions.contract.ViewContract;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.layout.Layout;
import rsp.compositions.routing.Router;

import java.util.List;
import java.util.Objects;

/**
 * Composition - Declares a feature domain's view placements and routes.
 * <p>
 * Each composition groups related views by declaring their contract factories and routes.
 * Lifecycle is derived automatically:
 * <ul>
 *   <li>Routed contracts (matched by Router) are eagerly instantiated</li>
 *   <li>Contracts required by the Layout are eagerly instantiated (companions)</li>
 *   <li>All other contracts are stored as lazy factories (for on-demand SHOW events)</li>
 * </ul>
 * <p>
 * Route resolution iterates Compositions in order - the first matching route wins.
 * <p>
 * Action handling is delegated to Contracts (e.g., EditViewContract.save(), delete()).
 */
public class Composition {
    private final Router router;
    private final List<ViewPlacement> views;
    private final Category categories;
    private final Layout layout;

    /**
     * Create a Composition with its router and view placements (default layout).
     */
    public Composition(Router router, ViewsPlacements placements) {
        this(router, placements, new Category(), new DefaultLayout());
    }

    /**
     * Create a Composition with its router, view placements, and explicit categories.
     */
    public Composition(Router router, ViewsPlacements placements, Category categories) {
        this(router, placements, categories, new DefaultLayout());
    }

    /**
     * Create a Composition with its router, view placements, categories, and layout.
     *
     * @param router The router for this composition's routes
     * @param placements The view placements builder
     * @param categories Explicit contract categories for navigation/title metadata
     * @param layout The layout strategy for visual arrangement
     */
    public Composition(Router router, ViewsPlacements placements, Category categories, Layout layout) {
        Objects.requireNonNull(router, "router cannot be null");
        Objects.requireNonNull(placements, "placements cannot be null");
        this.router = router;
        this.views = placements.toList();
        this.categories = Objects.requireNonNull(categories, "categories cannot be null");
        this.layout = Objects.requireNonNull(layout, "layout cannot be null");
    }

    /**
     * The router for this composition's routes.
     */
    public Router router() {
        return router;
    }

    /**
     * View placements for this composition.
     * Each placement declares a contract class and its factory.
     *
     * @return immutable list of ViewPlacements
     */
    public List<ViewPlacement> views() {
        return views;
    }

    /**
     * Categories used for navigation grouping and display metadata.
     */
    public Category categories() {
        return categories;
    }

    /**
     * The layout strategy for this composition.
     */
    public Layout layout() {
        return layout;
    }

    /**
     * Resolve display metadata for a contract class.
     */
    public ContractMetadata metadataFor(Class<? extends ViewContract> contractClass) {
        return categories.metadataFor(contractClass);
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
}
