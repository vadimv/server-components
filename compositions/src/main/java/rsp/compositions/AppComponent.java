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
    private final HttpRequest httpRequest;

    public AppComponent(UiRegistry uiRegistry, Router router, List<Module> modules, HttpRequest httpRequest) {
        super();
        this.uiRegistry = uiRegistry;
        this.router = router;
        this.modules = modules;
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
        return (context, state) -> context.with(Map.of(
            "app.router", router,
            "app.modules", modules,
            "app.uiRegistry", uiRegistry,
            "app.httpRequest", httpRequest
        ));
    }

    @Override
    public ComponentView<AppComponentState> componentView() {
        // RoutingComponent has default constructor - reads everything from context
        return _ -> _ -> new RoutingComponent();
    }

    public record AppComponentState() {
    }
}
