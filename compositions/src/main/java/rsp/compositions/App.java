package rsp.compositions;

import rsp.component.ComponentContext;
import rsp.component.View;
import rsp.component.definitions.InitialStateComponent;
import rsp.jetty.WebServer;
import rsp.server.http.HttpRequest;
import rsp.component.definitions.Component;
import rsp.dsl.Html;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class App {
    private final AppConfig config;
    private final UiRegistry uiRegistry;
    private final Router router;
    private final List<Module> modules;
    private final WebServer server;

    public App(AppConfig config, UiRegistry uiRegistry, Router router, Module... modules) {
        this.config = config;
        this.uiRegistry = uiRegistry;
        this.router = router;
        this.modules = Arrays.asList(modules);
        
        this.server = new WebServer(config.port(), this::rootComponent);
    }
    
    private Component<Object> rootComponent(HttpRequest request) {
        final String path = request.path.toString();
        final Optional<Class<? extends ViewContract>> contractClass = router.match(path);
        
        if (contractClass.isPresent()) {
            final Optional<UiComponent<ViewContract>> uiComponent = uiRegistry.findFor(contractClass.get());
            if (uiComponent.isPresent()) {
                Optional<ViewContract> contractInstance = findContractInstance(contractClass.get());
                
                if (contractInstance.isPresent()) {
                    ViewContract contract = contractInstance.get();
                    
                    // Populate context from query params
                    Map<String, Object> params = parseQueryParams(request.uri.getRawQuery());
                    ComponentContext context = ComponentContext.from(params);
                    contract.setContext(context);
                    
                    // Use raw type to bypass generic capture issues
                    UiComponent rawUiComponent = uiComponent.get();
                    return rawUiComponent.apply(contract);
                }
            }
        }

        final View<Object> notFoundView = state -> Html.html(Html.body(Html.div(Html.text("404 Not Found"))));
        return new InitialStateComponent<>(new Object(), notFoundView);
    }
    
    private Map<String, Object> parseQueryParams(String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> params = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }
    
    private Optional<ViewContract> findContractInstance(Class<? extends ViewContract> clazz) {
        for (Module module : modules) {
            for (ViewPlacement placement : module.views()) {
                if (clazz.isInstance(placement.contract())) {
                    return Optional.of(placement.contract());
                }
            }
        }
        return Optional.empty();
    }

    public void start() {
        server.start();
        server.join();
    }
}
