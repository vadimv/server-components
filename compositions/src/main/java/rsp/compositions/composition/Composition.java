package rsp.compositions.composition;

import rsp.compositions.contract.ViewContract;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.layout.Layout;
import rsp.compositions.routing.Router;

import java.util.Objects;

/**
 * Composition - Declares a feature domain's view placements and routes.
 * <p>
 * Each composition groups related views by declaring their contract factories and routes
 * through a {@link UiRegistry}. Lifecycle is derived automatically:
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
    private final UiRegistry uiRegistry;
    private final Category categories;
    private final Layout layout;

    /**
     * Create a Composition with its router and UI registry (default layout).
     */
    public Composition(Router router, UiRegistry uiRegistry) {
        this(router, uiRegistry, new Category(), new DefaultLayout());
    }

    /**
     * Create a Composition with its router, UI registry, and explicit categories.
     */
    public Composition(Router router, UiRegistry uiRegistry, Category categories) {
        this(router, uiRegistry, categories, new DefaultLayout());
    }

    /**
     * Create a Composition with its router, UI registry, categories, and layout.
     *
     * @param router     The router for this composition's routes
     * @param uiRegistry The registry holding contract and view factories
     * @param categories Explicit contract categories for navigation/title metadata
     * @param layout     The layout strategy for visual arrangement
     */
    public Composition(Router router, UiRegistry uiRegistry, Category categories, Layout layout) {
        Objects.requireNonNull(router, "router cannot be null");
        Objects.requireNonNull(uiRegistry, "uiRegistry cannot be null");
        this.router = router;
        this.uiRegistry = uiRegistry;
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
     * The UI registry holding contract factories and view factories for this composition.
     */
    public UiRegistry uiRegistry() {
        return uiRegistry;
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
}
