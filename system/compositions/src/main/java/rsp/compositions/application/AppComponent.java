package rsp.compositions.application;

import rsp.application.ApplicationConfig;
import rsp.application.ApplicationContext;
import rsp.authentication.Authentication;
import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.block.ContextKeys;
import rsp.compositions.composition.Composition;
import rsp.compositions.routing.UrlSyncComponent;
import rsp.url.RelativeUrl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;


public class AppComponent extends Component<AppComponent.AppComponentState, Object> {

    private final ApplicationContext applicationContext;
    private final List<Composition> compositions;
    private final RelativeUrl initialUrl;
    private final Authentication authentication;

    public AppComponent(ApplicationContext applicationContext,
                        List<Composition> compositions,
                        RelativeUrl initialUrl,
                        Authentication authentication) {
        super();
        this.applicationContext = Objects.requireNonNull(applicationContext);
        this.compositions = List.copyOf(Objects.requireNonNull(compositions));
        this.initialUrl = Objects.requireNonNull(initialUrl);
        this.authentication = Objects.requireNonNull(authentication);
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
            ComponentContext enrichedContext = context;
            for (Map.Entry<String, String> property : applicationContext.config().asMap().entrySet()) {
                enrichedContext = enrichedContext.with(
                        new ContextKey.StringKey<>(property.getKey(), String.class), property.getValue());
            }

            enrichedContext = enrichedContext
                .with(ApplicationConfig.class, applicationContext.config())
                .with(ApplicationContext.class, applicationContext)
                .with(Authentication.class, authentication)
                .with(ContextKeys.APP_COMPOSITIONS, compositions);

            enrichedContext = enrichedContext.with(applicationContext.services());

            return enrichedContext;
        };
    }

    @Override
    public ComponentView<AppComponentState, Object> componentView() {
        return _ -> _ -> new UrlSyncComponent(initialUrl);
    }

    public record AppComponentState() {
    }
}
