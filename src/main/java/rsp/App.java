package rsp;

import rsp.server.HttpRequest;
import rsp.services.PageRendering;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class App<S> {
    public static final String WS_ENDPOINT_PATH = "/bridge/web-socket/{pid}/{sid}";

    public final Function<HttpRequest, CompletableFuture<S>> routes;
    public final BiFunction<String, S, String> state2path;
    public final Map<QualifiedSessionId, Page<S>> pagesStorage;
    public final Component<S> rootComponent;

    public App(Function<HttpRequest, CompletableFuture<S>> routes,
               BiFunction<String, S, String> state2path,
               Map<QualifiedSessionId, Page<S>> pagesStorage,
               Component<S> rootComponent) {
        this.routes = routes;
        this.state2path = state2path;
        this.pagesStorage = pagesStorage;
        this.rootComponent = rootComponent;
    }

    public App(Function<HttpRequest, CompletableFuture<S>> routes,
               Component<S> rootComponent) {
        this(routes,
            (currentPath, s) -> currentPath,
            new ConcurrentHashMap<>(),
            rootComponent);
    }

    public App(S initialState,
               Component<S> rootComponent) {
        this(request -> CompletableFuture.completedFuture(initialState),
             (currentPath, s) -> currentPath,
             new ConcurrentHashMap<>(),
             rootComponent);
    }

    public PageRendering<S> pageRendering() {
        return new PageRendering(routes, state2path, pagesStorage, rootComponent);
    }
}

