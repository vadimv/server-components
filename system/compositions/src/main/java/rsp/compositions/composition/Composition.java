package rsp.compositions.composition;

import rsp.compositions.application.Services;
import rsp.compositions.layout.Layout;
import rsp.compositions.routing.Router;

import java.util.Objects;

/**
 * Composition - Declares a feature domain's view placements and routes.
 * <p>
 * Each composition groups related views by declaring their block factories and routes
 * through {@link Group}s. Lifecycle is derived automatically:
 * <ul>
 *   <li>Routed blocks (matched by Router) are eagerly instantiated</li>
 *   <li>Blocks required by the Layout are eagerly instantiated (companions)</li>
 *   <li>All other blocks are stored as lazy factories (for on-demand SHOW events)</li>
 * </ul>
 * <p>
 * Route resolution iterates Compositions in order - the first matching route wins.
 */
public class Composition {
    private final Router router;
    private final Group blocks;
    private final Layout layout;
    private final Services services;

    /**
     * Create a Composition with its router, layout, and groups.
     * Multiple groups are merged into a single group for lookup.
     *
     * @param router The router for this composition's routes
     * @param layout The layout strategy for visual arrangement
     * @param groups One or more groups holding block and view factories
     */
    public Composition(Router router, Layout layout, Group... groups) {
        this(router, layout, null, groups);
    }

    /**
     * Create a Composition with its router, layout, services, and groups.
     *
     * @param router   The router for this composition's routes
     * @param layout   The layout strategy for visual arrangement
     * @param services Composition-level services (nullable)
     * @param groups   One or more groups holding block and view factories
     */
    public Composition(Router router, Layout layout, Services services, Group... groups) {
        Objects.requireNonNull(router, "router cannot be null");
        Objects.requireNonNull(layout, "layout cannot be null");
        if (groups == null || groups.length == 0) {
            throw new IllegalArgumentException("at least one group is required");
        }
        this.router = router;
        this.layout = layout;
        this.services = services;
        if (groups.length == 1) {
            this.blocks = groups[0];
        } else {
            Group merged = new Group();
            for (Group group : groups) {
                merged.add(group);
            }
            this.blocks = merged;
        }
        validateAndSeal();
    }

    /**
     * The router for this composition's routes.
     */
    public Router router() {
        return router;
    }

    /**
     * The group holding block factories and view factories for this composition.
     */
    public Group blocks() {
        return blocks;
    }

    /**
     * The layout strategy for this composition.
     */
    public Layout layout() {
        return layout;
    }

    /**
     * Composition-level services (nullable).
     */
    public Services services() {
        return services;
    }

    private void validateAndSeal() {
        blocks.validateUniqueKeys();
        for (var route : router.routeTargets().entrySet()) {
            if (!blocks.hasBinding(route.getValue())) {
                throw new IllegalArgumentException("Route '" + route.getKey()
                        + "' targets unbound block key: " + route.getValue());
            }
        }
        for (Object required : layout.requiredBlockKeys()) {
            if (!blocks.hasBinding(required)) {
                throw new IllegalArgumentException("Layout requires unbound block key: " + required);
            }
        }
        blocks.seal();
        router.seal();
    }
}
