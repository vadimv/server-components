package rsp.compositions.application;

import rsp.compositions.composition.Composition;
import rsp.compositions.composition.UiRegistry;
import rsp.server.http.HttpRequest;
import rsp.component.definitions.Component;

import java.util.*;
import java.util.function.Function;

/**
 * App - Application entry point.
 * <p>
 * Registers services and compositions, creates AppComponent for each request.
 * Routes are defined within each Composition, not separately.
 */
public class App implements Function<HttpRequest, Component<?>> {
    private final AppConfig config;
    private final UiRegistry uiRegistry;
    private final List<Composition> compositions;
    private final Map<Class<?>, Object> services;

    public App(AppConfig config,
               UiRegistry uiRegistry,
               List<Composition> compositions,
               Services services) {
        this(config, uiRegistry, compositions, services.asMap());
    }

    public App(AppConfig config,
               UiRegistry uiRegistry,
               List<Composition> compositions,
               Map<Class<?>, Object> services) {
        this.config = Objects.requireNonNull(config);
        this.uiRegistry = Objects.requireNonNull(uiRegistry);
        this.compositions = Objects.requireNonNull(compositions);
        this.services = Objects.requireNonNull(services);
    }

    @Override
    public Component<?> apply(HttpRequest httpRequest) {
        return new AppComponent(config, uiRegistry, compositions, services, httpRequest);
    }
}
