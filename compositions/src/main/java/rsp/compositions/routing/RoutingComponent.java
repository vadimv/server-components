package rsp.compositions.routing;

import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;
import rsp.compositions.composition.Composition;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.SceneComponent;
import rsp.server.Path;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * RoutingComponent - Matches URL path to ViewContract classes by iterating Compositions.
 * <p>
 * This component:
 * 1. Reads url.path from context (populated by UrlSyncComponent/AutoAddressBarSyncComponent)
 * 2. Iterates Compositions in order, trying each one's Router
 * 3. First matching route wins - enriches context with route.composition, route.contractClass, route.path, route.pattern
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
     * Reads url.path from context (populated by UrlSyncComponent), iterates Compositions
     * to find a matching route, and enriches context with routing info.
     */
    @Override
    public BiFunction<ComponentContext, RoutingComponentState, ComponentContext> subComponentsContext() {
        return (context, state) -> {
            // Read compositions from context (populated by AppComponent)
            List<Composition> compositions = context.get(ContextKeys.APP_COMPOSITIONS);

            // Read url.path from context (populated by UrlSyncComponent/AutoAddressBarSyncComponent)
            Path path = context.get(ContextKeys.URL_PATH_FULL);

            if (compositions == null || path == null) {
                return context; // Graceful degradation
            }

            // Iterate Compositions in order - first match wins
            for (Composition composition : compositions) {
                Optional<Router.RouteMatch> match = composition.router().match(path);
                if (match.isPresent()) {
                    Router.RouteMatch routeMatch = match.get();
                    // Enrich context with composition and routing results
                    return context
                            .with(ContextKeys.ROUTE_COMPOSITION, composition)
                            .with(ContextKeys.ROUTE_CONTRACT_CLASS, routeMatch.contractClass())
                            .with(ContextKeys.ROUTE_PATH, path.toString())
                            .with(ContextKeys.ROUTE_PATTERN, routeMatch.pattern());
                }
            }

            throw new IllegalStateException("No route found for path: " + path);
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
