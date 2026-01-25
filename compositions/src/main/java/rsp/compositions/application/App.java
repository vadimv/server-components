package rsp.compositions.application;

import rsp.compositions.module.Module;
import rsp.compositions.routing.Router;
import rsp.compositions.module.UiRegistry;
import rsp.server.http.HttpRequest;
import rsp.component.definitions.Component;

import java.util.*;
import java.util.function.Function;

/**
 * App - Application entry point.
 * <p>
 * Registers services and modules, creates AppComponent for each request.
 */
public class App implements Function<HttpRequest, Component<?>> {
    private final AppConfig config;
    private final UiRegistry uiRegistry;
    private final Router router;
    private final List<Module> modules;
    private final List<Object> services;

    public App(AppConfig config, UiRegistry uiRegistry, Router router, List<Module> modules, List<Object> services) {
        this.config = config;
        this.uiRegistry = uiRegistry;
        this.router = router;
        this.modules = modules;
        this.services = services;
    }

    @Override
    public Component<?> apply(HttpRequest httpRequest) {
        return new AppComponent(config, uiRegistry, router, modules, services, httpRequest);
    }
}
