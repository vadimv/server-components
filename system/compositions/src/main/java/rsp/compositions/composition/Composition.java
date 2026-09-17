package rsp.compositions.composition;

import rsp.compositions.block.BlockTarget;
import rsp.compositions.layout.Layout;
import rsp.compositions.routing.BlockRoutes;
import rsp.url.routing.RouteTable;

import java.util.Objects;

/**
 * Composition - Declares a feature domain's view placements and routes.
 * <p>
 * Each composition groups related views by declaring their block factories and routes
 * through {@link Group}s. Lifecycle is derived automatically:
 * <ul>
 *   <li>Routed blocks (matched by the route table) are eagerly instantiated</li>
 *   <li>Blocks required by the Layout are eagerly instantiated (companions)</li>
 *   <li>All other blocks are stored as lazy factories (for on-demand SHOW events)</li>
 * </ul>
 * <p>
 * Route resolution iterates Compositions in order - the first matching route wins.
 */
public class Composition {
    private final RouteTable<BlockTarget> routes;
    private final Group blocks;
    private final Layout layout;

    /**
     * Create a Composition with its route table, layout, and groups.
     * Multiple groups are merged into a single group for lookup.
     *
     * @param routes The immutable table for this composition's routes
     * @param layout The layout strategy for visual arrangement
     * @param groups One or more groups holding block and view factories
     */
    public Composition(RouteTable<BlockTarget> routes, Layout layout, Group... groups) {
        Objects.requireNonNull(routes, "routes cannot be null");
        Objects.requireNonNull(layout, "layout cannot be null");
        if (groups == null || groups.length == 0) {
            throw new IllegalArgumentException("at least one group is required");
        }
        this.routes = routes;
        this.layout = layout;
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

    public Composition(BlockRoutes.Builder routes, Layout layout, Group... groups) {
        this(Objects.requireNonNull(routes, "routes").build(), layout, groups);
    }

    /**
     * The immutable table for this composition's routes.
     */
    public RouteTable<BlockTarget> routes() {
        return routes;
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

    private void validateAndSeal() {
        blocks.validateUniqueKeys();
        for (var route : routes.routes()) {
            if (!blocks.hasBinding(route.target().key())) {
                throw new IllegalArgumentException("Route '" + route.template()
                        + "' targets unbound block key: " + route.target().key());
            }
            BlockTarget binding = blocks.target(route.target().key());
            if (!binding.blockClass().equals(route.target().blockClass())) {
                throw new IllegalArgumentException("Route '" + route.template()
                        + "' target class does not match binding for key: " + route.target().key());
            }
        }
        for (Object required : layout.requiredBlockKeys()) {
            if (!blocks.hasBinding(required)) {
                throw new IllegalArgumentException("Layout requires unbound block key: " + required);
            }
        }
        blocks.seal();
    }
}
