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

    private final AppConfig config;
    private final UiRegistry uiRegistry;
    private final Router router;
    private final List<Module> modules;
    private final Map<String, Object> services;
    private final HttpRequest httpRequest;

    public AppComponent(AppConfig config, UiRegistry uiRegistry, Router router, List<Module> modules, Map<String, Object> services, HttpRequest httpRequest) {
        super();
        this.config = config;
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
            // Add app-level objects using ClassKey (ServiceLoader style)
            ComponentContext enrichedContext = context
                .with(AppConfig.class, config)
                .with(Router.class, router)
                .with(HttpRequest.class, httpRequest)
                .with(ContextKeys.UI_REGISTRY, uiRegistry)
                .with(ContextKeys.APP_MODULES, modules);

            // Add generic configuration values for framework components
            // This allows contracts to be agnostic of AppConfig structure
            rsp.component.ContextKey<Integer> sc = new rsp.component.ContextKey.StringKey<>(ListViewContract.CONFIG_DEFAULT_PAGE_SIZE, Integer.class);
            enrichedContext = enrichedContext
                .with(sc, Integer.valueOf(config.defaultPageSize()));

            // Add all services to context using ClassKey for each service instance
            for (Map.Entry<String, Object> entry : services.entrySet()) {
                // Services are stored by their actual class, not by string key
                Object service = entry.getValue();
                if (service != null) {
                    @SuppressWarnings("unchecked")
                    Class<Object> serviceClass = (Class<Object>) service.getClass();
                    enrichedContext = enrichedContext.with(serviceClass, service);
                }
            }

            return enrichedContext;
        };
    }

    @Override
    public ComponentView<AppComponentState> componentView() {
        // AuthComponent has default constructor - reads everything from context
        return _ -> _ -> new AuthComponent();
    }

    public record AppComponentState() {
    }
}
