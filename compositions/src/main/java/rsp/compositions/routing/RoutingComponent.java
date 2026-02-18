package rsp.compositions.routing;

import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;
import rsp.compositions.composition.Composition;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.SceneComponent;
import rsp.compositions.contract.ViewContract;
import rsp.server.Path;
import rsp.server.http.NotFoundException;

import java.util.List;
import java.util.Objects;
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

    /**
     * Match URL path to a route at component init time.
     * Reads compositions and URL path from context, finds the first matching route,
     * and stores the match result in state for use by subComponentsContext() and componentView().
     */
    @Override
    public ComponentStateSupplier<RoutingComponentState> initStateSupplier() {
        return (_, context) -> {
            List<Composition> compositions = context.get(ContextKeys.APP_COMPOSITIONS);
            Path path = context.get(ContextKeys.URL_PATH_FULL);

            if (compositions == null || path == null) {
                throw new IllegalStateException("Missing APP_COMPOSITIONS or URL_PATH_FULL in context");
            }

            for (Composition composition : compositions) {
                Optional<Router.RouteMatch> match = composition.router().match(path);
                if (match.isPresent()) {
                    Router.RouteMatch routeMatch = match.get();
                    return new RoutingComponentState(composition,
                                                     routeMatch.contractClass(),
                                                     path.toString(),
                                                     routeMatch.pattern());
                }
            }

            throw new NotFoundException("No route found for path: " + path);
        };
    }

    /**
     * Enrich context with routing results from state.
     * Downstream components (ExplorerContract, ListView, FormViewContract) read these keys from context.
     */
    @Override
    public BiFunction<ComponentContext, RoutingComponentState, ComponentContext> subComponentsContext() {
        return (context, state) -> context
                .with(ContextKeys.ROUTE_COMPOSITION, state.composition())
                .with(ContextKeys.ROUTE_CONTRACT_CLASS, state.contractClass())
                .with(ContextKeys.ROUTE_PATH, state.path())
                .with(ContextKeys.ROUTE_PATTERN, state.pattern());
    }

    @Override
    public ComponentView<RoutingComponentState> componentView() {
        return _ -> state -> new SceneComponent(state.path().toString(),
                                                                                                      state.composition(),
                                                                                                      state.contractClass(),
                                                                                                      state.pattern(),
                                                                                                      state.composition().layout());
    }

    public record RoutingComponentState(
            Composition composition,
            Class<? extends ViewContract> contractClass,
            String path,
            String pattern
    ) {
        public RoutingComponentState {
            Objects.requireNonNull(composition, "composition");
            Objects.requireNonNull(contractClass, "contractClass");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(pattern, "pattern");
        }
    }
}
