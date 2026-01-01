package rsp.compositions;

import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;
import rsp.server.http.HttpRequest;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;


public class AppComponent extends Component<AppComponent.AppComponentState> {

    private final UiRegistry uiRegistry;
    private final Router router;
    private final List<Module> modules;
    private final Map<String, Object> services;
    private final HttpRequest httpRequest;

    public AppComponent(UiRegistry uiRegistry, Router router, List<Module> modules, Map<String, Object> services, HttpRequest httpRequest) {
        super();
        this.uiRegistry = uiRegistry;
        this.router = router;
        this.modules = modules;
        this.services = services;
        this.httpRequest = httpRequest;
    }

    @Override
    public ComponentStateSupplier<AppComponentState> initStateSupplier() {
        return (_, _) -> new AppComponentState();
    }

    /**
     * Enrich context with application-level objects.
     * This is where constructor injection stops and pure context propagation begins.
     */
    @Override
    public BiFunction<ComponentContext, AppComponentState, ComponentContext> subComponentsContext() {
        return (context, state) -> {
            // Build context map with app-level and service objects
            Map<String, Object> contextMap = new java.util.HashMap<>();
            contextMap.put("app.router", router);
            contextMap.put("app.modules", modules);
            contextMap.put("app.uiRegistry", uiRegistry);
            contextMap.put("app.httpRequest", httpRequest);

            // Add all services to context with their namespace keys
            contextMap.putAll(services);

            return context.with(contextMap);
        };
    }

    @Override
    public ComponentView<AppComponentState> componentView() {
        // RoutingComponent has default constructor - reads everything from context
        return _ -> _ -> new RoutingComponent();
    }

    public record AppComponentState() {
    }
}
