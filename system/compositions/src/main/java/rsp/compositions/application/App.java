package rsp.compositions.application;

import rsp.compositions.composition.Composition;
import rsp.compositions.auth.AuthComponent;
import rsp.component.definitions.Component;
import rsp.url.RelativeUrl;

import java.util.*;
import java.util.function.Function;

/**
 * App - Application entry point.
 * <p>
 * Registers services and compositions, creates AppComponent for each request.
 * Routes and UI registries are defined within each Composition.
 */
public class App implements Function<RelativeUrl, Component<?, ?>> {
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
    public Component<?, ?> apply(RelativeUrl initialUrl) {
        return apply(initialUrl, AuthComponent.AuthResult.anonymous());
    }

    public Component<?, ?> apply(RelativeUrl initialUrl, AuthComponent.AuthResult identity) {
        return new AppComponent(config, compositions, services, initialUrl, identity);
    }
}
