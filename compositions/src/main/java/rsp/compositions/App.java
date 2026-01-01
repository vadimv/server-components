package rsp.compositions;

import rsp.server.http.HttpRequest;
import rsp.component.definitions.Component;

import java.util.*;
import java.util.function.Function;

public class App implements Function<HttpRequest, Component<?>> {
    private final AppConfig config;
    private final UiRegistry uiRegistry;
    private final Router router;
    private final List<Module> modules;

    public App(AppConfig config, UiRegistry uiRegistry, Router router, Module... modules) {
        this.config = config;
        this.uiRegistry = uiRegistry;
        this.router = router;
        this.modules = Arrays.asList(modules);
    }

    @Override
    public Component<?> apply(HttpRequest httpRequest) {
        return new AppComponent(uiRegistry, router, modules, httpRequest);
    }


}
