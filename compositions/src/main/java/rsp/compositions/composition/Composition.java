package rsp.compositions.composition;

import rsp.compositions.layout.Layout;
import rsp.compositions.routing.Router;

import java.util.Objects;

/**
 * Composition - Declares a feature domain's view placements and routes.
 * <p>
 * Each composition groups related views by declaring their contract factories and routes
 * through {@link Group}s. Lifecycle is derived automatically:
 * <ul>
 *   <li>Routed contracts (matched by Router) are eagerly instantiated</li>
 *   <li>Contracts required by the Layout are eagerly instantiated (companions)</li>
 *   <li>All other contracts are stored as lazy factories (for on-demand SHOW events)</li>
 * </ul>
 * <p>
 * Route resolution iterates Compositions in order - the first matching route wins.
 */
public class Composition {
    private final Router router;
    private final Group contracts;
    private final Layout layout;

    /**
     * Create a Composition with its router, layout, and groups.
     * Multiple groups are merged into a single group for lookup.
     *
     * @param router The router for this composition's routes
     * @param layout The layout strategy for visual arrangement
     * @param groups One or more groups holding contract and view factories
     */
    public Composition(Router router, Layout layout, Group... groups) {
        Objects.requireNonNull(router, "router cannot be null");
        Objects.requireNonNull(layout, "layout cannot be null");
        if (groups == null || groups.length == 0) {
            throw new IllegalArgumentException("at least one group is required");
        }
        this.router = router;
        this.layout = layout;
        if (groups.length == 1) {
            this.contracts = groups[0];
        } else {
            Group merged = new Group();
            for (Group group : groups) {
                merged.add(group);
            }
            this.contracts = merged;
        }
    }

    /**
     * The router for this composition's routes.
     */
    public Router router() {
        return router;
    }

    /**
     * The group holding contract factories and view factories for this composition.
     */
    public Group contracts() {
        return contracts;
    }

    /**
     * The layout strategy for this composition.
     */
    public Layout layout() {
        return layout;
    }
}
