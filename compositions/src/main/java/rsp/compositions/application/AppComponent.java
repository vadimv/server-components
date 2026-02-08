package rsp.compositions.application;

import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.ContextKey;
import rsp.component.definitions.Component;
import rsp.compositions.auth.AuthComponent;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.ListViewContract;
import rsp.compositions.contract.NavigationEntry;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.UiRegistry;
import rsp.server.http.HttpRequest;

import java.util.List;
import java.util.function.BiFunction;


public class AppComponent extends Component<AppComponent.AppComponentState> {

    private final AppConfig config;
    private final UiRegistry uiRegistry;
    private final List<Composition> compositions;
    private final List<Object> services;
    private final HttpRequest httpRequest;

    public AppComponent(AppConfig config, UiRegistry uiRegistry, List<Composition> compositions, List<Object> services, HttpRequest httpRequest) {
        super();
        this.config = config;
        this.uiRegistry = uiRegistry;
        this.compositions = compositions;
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
     * Note: Router is inside each Composition, not at app level.
     */
    @Override
    public BiFunction<ComponentContext, AppComponentState, ComponentContext> subComponentsContext() {
        return (context, state) -> {
            // Add app-level objects using ClassKey (ServiceLoader style)
            ComponentContext enrichedContext = context
                .with(AppConfig.class, config)
                .with(HttpRequest.class, httpRequest)
                .with(ContextKeys.UI_REGISTRY, uiRegistry)
                .with(ContextKeys.APP_COMPOSITIONS, compositions)
                .with(ContextKeys.NAVIGATION_ENTRIES, NavigationEntry.fromCompositions(compositions));

            // Add generic configuration values for framework components
            // This allows contracts to be agnostic of AppConfig structure
            ContextKey<Integer> sc = new ContextKey.StringKey<>(ListViewContract.CONFIG_DEFAULT_PAGE_SIZE, Integer.class);
            enrichedContext = enrichedContext.with(sc, Integer.valueOf(config.defaultPageSize()));

            // Add all services to context using their actual classes as keys for each service instance
            enrichedContext = enrichedContext.with(services);

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
