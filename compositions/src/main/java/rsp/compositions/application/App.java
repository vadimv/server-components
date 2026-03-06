package rsp.compositions.application;

import rsp.compositions.composition.Composition;
import rsp.server.http.HttpRequest;
import rsp.component.definitions.Component;

import java.util.*;
import java.util.function.Function;

/**
 * App - Application entry point.
 * <p>
 * Registers services and compositions, creates AppComponent for each request.
 * Routes and UI registries are defined within each Composition.
 */
public class App implements Function<HttpRequest, Component<?>> {
    private final Config config;
    private final List<Composition> compositions;
    private final Map<Class<?>, Object> services;

    public App(Config config,
               List<Composition> compositions,
               Services services) {
        this(config, compositions, services.asMap());
    }

    public App(Config config,
               List<Composition> compositions,
               Map<Class<?>, Object> services) {
        this.config = Objects.requireNonNull(config);
        this.compositions = Objects.requireNonNull(compositions);
        this.services = Objects.requireNonNull(services);
    }

    @Override
    public Component<?> apply(HttpRequest httpRequest) {
        return new AppComponent(config, compositions, services, httpRequest);
    }
}
