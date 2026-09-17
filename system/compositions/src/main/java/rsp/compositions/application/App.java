package rsp.compositions.application;

import rsp.application.ApplicationContext;
import rsp.application.ApplicationLifecycle;
import rsp.compositions.composition.Composition;
import rsp.compositions.auth.AuthComponent;
import rsp.component.definitions.Component;
import rsp.url.RelativeUrl;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * App - Application entry point.
 * <p>
 * Registers services and compositions, creates AppComponent for each request.
 * Routes and UI registries are defined within each Composition.
 */
public final class App implements Function<RelativeUrl, Component<?, ?>>, ApplicationLifecycle {
    private final ApplicationContext applicationContext;
    private final List<Composition> compositions;

    public App(ApplicationContext applicationContext, List<Composition> compositions) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext");
        this.compositions = List.copyOf(Objects.requireNonNull(compositions, "compositions"));
    }

    @Override
    public Component<?, ?> apply(RelativeUrl initialUrl) {
        return apply(initialUrl, AuthComponent.AuthResult.anonymous());
    }

    public Component<?, ?> apply(RelativeUrl initialUrl, AuthComponent.AuthResult identity) {
        return new AppComponent(applicationContext, compositions, initialUrl, identity);
    }

    public ApplicationContext applicationContext() {
        return applicationContext;
    }

    @Override
    public void start() {
        applicationContext.start();
    }

    @Override
    public void stop() {
        applicationContext.stop();
    }
}
