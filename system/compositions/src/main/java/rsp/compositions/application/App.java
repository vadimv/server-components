package rsp.compositions.application;

import rsp.application.ApplicationContext;
import rsp.application.ApplicationLifecycle;
import rsp.authentication.Authentication;
import rsp.compositions.composition.Composition;
import rsp.component.definitions.Component;
import rsp.url.RelativeUrl;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * App - Application entry point.
 * <p>
 * Combines application context, request identity, and compositions into one root component.
 * Routes and UI registries are defined within each Composition.
 */
public final class App implements BiFunction<RelativeUrl, Authentication, Component<?, ?>>, ApplicationLifecycle {
    private final ApplicationContext applicationContext;
    private final List<Composition> compositions;

    public App(ApplicationContext applicationContext, List<Composition> compositions) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext");
        this.compositions = List.copyOf(Objects.requireNonNull(compositions, "compositions"));
    }

    @Override
    public Component<?, ?> apply(RelativeUrl initialUrl, Authentication authentication) {
        return new AppComponent(applicationContext, compositions, initialUrl, authentication);
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
