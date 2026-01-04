package rsp.compositions;

import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.AutoAddressBarSyncComponent;
import rsp.component.definitions.Component;
import rsp.server.http.HttpRequest;
import rsp.server.http.RelativeUrl;

import java.util.function.BiFunction;

public class RoutingComponent extends Component<RoutingComponent.RoutingComponentState> {

    /**
     * Default constructor - reads everything from ComponentContext.
     * This is a generic framework component with no application-specific dependencies.
     */
    public RoutingComponent() {
        super();
    }

    @Override
    public ComponentStateSupplier<RoutingComponentState> initStateSupplier() {
        return (_, context) -> {
            // Read httpRequest from context and store in state
            HttpRequest httpRequest = context.get(HttpRequest.class);
            return new RoutingComponentState(httpRequest);
        };
    }

    /**
     * Enrich context with routing results.
     * Reads router and httpRequest from context, matches the route, and enriches context with routing info.
     */
    @Override
    public BiFunction<ComponentContext, RoutingComponentState, ComponentContext> subComponentsContext() {
        return (context, state) -> {
            // Read application objects from context (populated by AppComponent)
            Router router = context.get(Router.class);
            HttpRequest httpRequest = context.get(HttpRequest.class);

            if (router == null || httpRequest == null) {
                return context; // Graceful degradation
            }

            // Match route to get contract class and pattern
            String path = httpRequest.path.toString();
            Router.RouteMatch routeMatch = router.match(path)
                .orElseThrow(() -> new IllegalStateException("No route found for path: " + path));

            // Enrich context with routing results INCLUDING pattern
            return context.with(ContextKeys.ROUTE_CONTRACT_CLASS, routeMatch.contractClass())
                .with(ContextKeys.ROUTE_PATH, path)
                .with(ContextKeys.ROUTE_PATTERN, routeMatch.pattern());
        };
    }

    @Override
    public ComponentView<RoutingComponentState> componentView() {
        return _ -> state -> {
            // Read httpRequest from state
            HttpRequest httpRequest = state.httpRequest();

            if (httpRequest == null) {
                throw new IllegalStateException("HttpRequest not found in state");
            }

            // Use AutoAddressBarSyncComponent which auto-populates ALL URL data
            return new AutoAddressBarSyncComponent(httpRequest.relativeUrl()) {
                @Override
                public ComponentView<RelativeUrl> componentView() {
                    // ServicesComponent has default constructor - reads from context
                    return _ -> _ -> new ServicesComponent();
                }
            };
        };
    }


    public record RoutingComponentState(HttpRequest httpRequest) {
    }
}
