package rsp.compositions.routing;

import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.SceneComponent;
import rsp.server.Path;

import java.util.function.BiFunction;

/**
 * RoutingComponent - Matches URL path to ViewContract classes.
 * <p>
 * This component:
 * 1. Reads url.path from context (populated by UrlSyncComponent/AutoAddressBarSyncComponent)
 * 2. Matches the path against registered routes using Router
 * 3. Enriches context with route.contractClass, route.path, route.pattern
 * 4. Renders SceneComponent
 * <p>
 * Position in component chain: AuthComponent → UrlSyncComponent → RoutingComponent → SceneComponent
 * <p>
 * Note: This component does NOT depend on HttpRequest - it reads the path from context,
 * allowing for better separation of concerns and testability.
 */
public class RoutingComponent extends Component<RoutingComponent.RoutingComponentState> {

    public RoutingComponent() {
        super();
    }

    @Override
    public ComponentStateSupplier<RoutingComponentState> initStateSupplier() {
        return (_, _) -> new RoutingComponentState();
    }

    /**
     * Enrich context with routing results.
     * Reads url.path from context (populated by UrlSyncComponent), matches the route,
     * and enriches context with routing info.
     */
    @Override
    public BiFunction<ComponentContext, RoutingComponentState, ComponentContext> subComponentsContext() {
        return (context, state) -> {
            // Read router from context (populated by AppComponent)
            Router router = context.get(Router.class);

            // Read url.path from context (populated by UrlSyncComponent/AutoAddressBarSyncComponent)
            Path path = context.get(ContextKeys.URL_PATH_FULL);

            if (router == null || path == null) {
                return context; // Graceful degradation
            }

            // Match route to get contract class and pattern
            Router.RouteMatch routeMatch = router.match(path)
                .orElseThrow(() -> new IllegalStateException("No route found for path: " + path));

            // Enrich context with routing results
            return context.with(ContextKeys.ROUTE_CONTRACT_CLASS, routeMatch.contractClass())
                .with(ContextKeys.ROUTE_PATH, path.toString())
                .with(ContextKeys.ROUTE_PATTERN, routeMatch.pattern());
        };
    }

    @Override
    public ComponentView<RoutingComponentState> componentView() {
        // SceneComponent builds scene and stores in state
        return _ -> _ -> new SceneComponent();
    }

    public record RoutingComponentState() {
    }
}
