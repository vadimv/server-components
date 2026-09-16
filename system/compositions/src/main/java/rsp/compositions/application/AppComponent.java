package rsp.compositions.application;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.block.ContextKeys;
import rsp.compositions.auth.AuthComponent;
import rsp.compositions.composition.Composition;
import rsp.compositions.routing.UrlSyncComponent;
import rsp.url.RelativeUrl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;


public class AppComponent extends Component<AppComponent.AppComponentState, Object> {

    private final Config config;
    private final List<Composition> compositions;
    private final Map<Class<?>, Object> services;
    private final RelativeUrl initialUrl;
    private final AuthComponent.AuthResult identity;

    public AppComponent(Config config,
                        List<Composition> compositions,
                        Map<Class<?>, Object> services,
                        RelativeUrl initialUrl,
                        AuthComponent.AuthResult identity) {
        super();
        this.config = Objects.requireNonNull(config);
        this.compositions = Objects.requireNonNull(compositions);
        this.services = Objects.requireNonNull(services);
        this.initialUrl = Objects.requireNonNull(initialUrl);
        this.identity = Objects.requireNonNull(identity);
    }

    @Override
    public ComponentStateSupplier<AppComponentState> initStateSupplier() {
        return (_, _) -> new AppComponentState();
    }

    /**
     * Enrich context with application-level objects.
     * This is where constructor injection stops and pure context propagation begins.
     * Note: routes and blocks are inside each Composition, not at app level.
     */
    @Override
    public BiFunction<ComponentContext, AppComponentState, ComponentContext> subComponentsContext() {
        return (context, state) -> {
            // Inject all config properties into context as StringKey<String> entries
            ComponentContext enrichedContext = config.applyTo(context);

            // Add app-level objects using ClassKey (ServiceLoader style)
            enrichedContext = enrichedContext
                .with(Config.class, config)
                .with(ContextKeys.AUTH_RESULT, identity)
                .with(ContextKeys.APP_COMPOSITIONS, compositions);

            // Add all services to context using their actual classes as keys for each service instance
            enrichedContext = enrichedContext.with(services);

            return enrichedContext;
        };
    }

    @Override
    public ComponentView<AppComponentState, Object> componentView() {
        return _ -> _ -> new UrlSyncComponent(initialUrl);
    }

    @Override
    public void onMounted(ComponentCompositeKey componentId, AppComponentState state,
                          StateUpdater<AppComponentState> stateUpdate) {
        Lookup lookup = new ServiceMapLookup(services);
        for (Object service : services.values()) {
            if (service instanceof ServicesLifecycleHandler handler) {
                handler.onStart(lookup);
            }
        }
    }

    @Override
    public void onUnmounted(ComponentCompositeKey componentId, AppComponentState state) {
        for (Object service : services.values()) {
            if (service instanceof ServicesLifecycleHandler handler) {
                handler.onStop();
            }
        }
    }

    public record AppComponentState() {
    }
}
