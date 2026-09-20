package rsp.http;

import rsp.application.ApplicationLifecycle;
import rsp.component.definitions.Component;
import rsp.metrics.MetricNames;
import rsp.metrics.MetricObject;
import rsp.metrics.MetricObjectTypes;
import rsp.metrics.Metrics;
import rsp.page.DefaultEventLoop;
import rsp.page.EventLoop;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderedPage;
import rsp.server.socket.SocketServerObserver;
import rsp.server.socket.SocketWebServer;
import rsp.http.routing.HttpPrefixHandler;
import rsp.http.routing.HttpRouteHandler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/** UI page/session adapter backed by the UI-independent socket HTTP/WebSocket transport. */
public class WebServer implements ApplicationLifecycle {
    public static final int DEFAULT_CONNECTION_LIMIT = SocketWebServer.DEFAULT_CONNECTION_LIMIT;
    public static final int DEFAULT_HEARTBEAT_INTERVAL_MS = 10_000;
    static final int WEB_SOCKET_CLOSE_GRACE_TIMEOUT_MS = SocketWebServer.WEB_SOCKET_CLOSE_GRACE_TIMEOUT_MS;
    static final String WEB_SOCKET_SERVER_STOP_REASON = "Server stopping";

    /** Rendered pages waiting for their first WebSocket session to bind. */
    public final Map<QualifiedSessionId, RenderedPage> pagesStorage = new ConcurrentHashMap<>();

    private final int requestedPort;
    private PageApplication pageApplication = _ ->
            HttpResponse.status(HttpStatus.NOT_FOUND).build();
    private Optional<StaticResources> staticResources = Optional.empty();
    private Optional<SslConfiguration> sslConfiguration = Optional.empty();
    private int connectionLimit = DEFAULT_CONNECTION_LIMIT;
    private Supplier<EventLoop> eventLoopSupplier = DefaultEventLoop::new;
    private LocalSessionResumeConfig localSessionResumeConfig = LocalSessionResumeConfig.defaults();
    private Metrics metrics = Metrics.noop();
    private final rsp.http.routing.HttpRouter.Builder applicationRoutes =
            rsp.http.routing.HttpRouter.builder();
    private final List<HttpRouter.ResultRegistration> resultRoutes = new ArrayList<>();
    private final List<HttpMiddleware> middleware = new ArrayList<>();

    private boolean configurationFrozen;
    private LocalSessionRegistry localSessionRegistry;
    private Optional<StaticResourceHandler> staticResourceHandler;
    private PageHttpHandler httpHandler;
    private HttpApplication httpApplication;
    private SocketWebServer transport;

    /** Creates a server configuration. Fluent configuration is frozen by the first runtime operation. */
    public WebServer(int port) {
        this.requestedPort = port;
    }

    /** Sets the page/response application used when no explicit HTTP route handles the request. */
    public synchronized WebServer pageApplication(PageApplication pageApplication) {
        requireConfigurable();
        this.pageApplication = Objects.requireNonNull(pageApplication, "pageApplication");
        return this;
    }

    /** Sets a live UI component factory as the fallback page application. */
    public synchronized WebServer page(
            Function<HttpRequest, ? extends Component<?, ?>> componentFactory) {
        requireConfigurable();
        Objects.requireNonNull(componentFactory, "componentFactory");
        this.pageApplication = request -> PageResult.live(componentFactory.apply(request));
        return this;
    }

    /** Adds routes handled before framework assets and the UI page fallback. */
    public synchronized WebServer routes(rsp.http.routing.HttpRouter routes) {
        requireConfigurable();
        applicationRoutes.include(Objects.requireNonNull(routes, "routes"));
        return this;
    }

    /** Adds ordinary HTTP endpoints and UI pages to one method-aware route graph. */
    public synchronized WebServer routes(Router router) {
        requireConfigurable();
        HttpRouter routes = (HttpRouter) Objects.requireNonNull(router, "router");
        applicationRoutes.include(routes.responseRoutes());
        resultRoutes.addAll(routes.resultRoutes());
        return this;
    }

    public synchronized WebServer staticResources(StaticResources staticResources) {
        requireConfigurable();
        this.staticResources = Optional.of(Objects.requireNonNull(staticResources, "staticResources"));
        return this;
    }

    public synchronized WebServer ssl(SslConfiguration sslConfiguration) {
        requireConfigurable();
        this.sslConfiguration = Optional.of(Objects.requireNonNull(sslConfiguration, "sslConfiguration"));
        return this;
    }

    public synchronized WebServer connectionLimit(int connectionLimit) {
        requireConfigurable();
        this.connectionLimit = requirePositiveConnectionLimit(connectionLimit);
        return this;
    }

    public synchronized WebServer eventLoops(Supplier<EventLoop> eventLoopSupplier) {
        requireConfigurable();
        this.eventLoopSupplier = Objects.requireNonNull(eventLoopSupplier, "eventLoopSupplier");
        return this;
    }

    public synchronized WebServer localSessionResume(LocalSessionResumeConfig config) {
        requireConfigurable();
        this.localSessionResumeConfig = Objects.requireNonNull(config, "config");
        return this;
    }

    public synchronized WebServer metrics(Metrics metrics) {
        requireConfigurable();
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        return this;
    }

    /** Adds cross-cutting behavior around application, framework, and page routes. */
    public synchronized WebServer middleware(HttpMiddleware value) {
        requireConfigurable();
        middleware.add(Objects.requireNonNull(value, "value"));
        return this;
    }

    public synchronized void start() {
        initializeRuntime();
        if (sslConfiguration.isPresent()) {
            throw new UnsupportedOperationException("TLS is not implemented in server-socket yet");
        }
        if (transport.isRunning()) {
            throw new IllegalStateException("WebServer is already running");
        }
        boolean applicationStarted = false;
        try {
            pageApplication.start();
            applicationStarted = true;
            localSessionRegistry.start();
            transport.start();
        } catch (RuntimeException | Error failure) {
            localSessionRegistry.closeAll();
            if (applicationStarted) {
                try {
                    pageApplication.stop();
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    public void join() {
        initializeRuntime();
        transport.join();
    }

    public synchronized void stop() {
        if (transport == null) {
            return;
        }
        pagesStorage.clear();
        try {
            transport.stop();
        } finally {
            try {
                localSessionRegistry.closeAll();
            } finally {
                pageApplication.stop();
            }
        }
    }

    public synchronized int port() {
        return transport == null ? requestedPort : transport.port();
    }

    protected PageApplication rootComponentDefinition() {
        return pageApplication;
    }

    protected Optional<StaticResources> staticResources() {
        return staticResources;
    }

    protected Optional<SslConfiguration> sslConfiguration() {
        return sslConfiguration;
    }

    protected int connectionLimit() {
        return connectionLimit;
    }

    protected Supplier<EventLoop> eventLoopSupplier() {
        return eventLoopSupplier;
    }

    protected LocalSessionResumeConfig localSessionResumeConfig() {
        return localSessionResumeConfig;
    }

    protected Metrics metrics() {
        return metrics;
    }

    protected Optional<StaticResourceHandler> staticResourceHandler() {
        initializeRuntime();
        return staticResourceHandler;
    }

    protected PageHttpHandler httpHandler() {
        initializeRuntime();
        return httpHandler;
    }

    protected HttpApplication httpApplication() {
        initializeRuntime();
        return httpApplication;
    }

    int activeWebSocketCount() {
        initializeRuntime();
        return transport.activeWebSocketCount();
    }

    int liveSessionCount() {
        initializeRuntime();
        return localSessionRegistry.size();
    }

    private synchronized void initializeRuntime() {
        if (transport != null) {
            return;
        }
        if (configurationFrozen) {
            throw new IllegalStateException("WebServer runtime initialization previously failed");
        }
        configurationFrozen = true;
        localSessionRegistry = new LocalSessionRegistry(pagesStorage, eventLoopSupplier,
                localSessionResumeConfig, metrics);
        staticResourceHandler = staticResources.map(resources ->
                new StaticResourceHandler(resources.resourcesBaseDir(), resources.contextPath()));
        httpHandler = new PageHttpHandler(pagesStorage, pageApplication,
                DEFAULT_HEARTBEAT_INTERVAL_MS, metrics);
        rsp.http.routing.HttpRouter.Builder routesBuilder = rsp.http.routing.HttpRouter.builder()
                .include(applicationRoutes.build());
        addResultRoutes(routesBuilder, resultRoutes, httpHandler);
        HttpApplication routes = routesBuilder
                .fallback(uiRoutes(httpHandler, staticResources, staticResourceHandler))
                .build();
        httpApplication = HttpMiddleware.pipeline(routes, middleware);
        transport = new SocketWebServer(requestedPort, httpApplication,
                List.of(new RspWebSocketEndpoint(localSessionRegistry)), connectionLimit,
                DEFAULT_HEARTBEAT_INTERVAL_MS * 3, transportObserver(metrics));
    }

    private void requireConfigurable() {
        if (configurationFrozen) {
            throw new IllegalStateException("WebServer configuration is already frozen");
        }
    }

    private static SocketServerObserver transportObserver(Metrics metrics) {
        return new SocketServerObserver() {
            @Override
            public void requestReceived() {
                metrics.incrementCounter(MetricNames.HTTP_REQUESTS);
            }

            @Override
            public void requestFailed() {
                metrics.incrementCounter(MetricNames.HTTP_FAILURES);
            }

            @Override
            public void activeWebSocketsChanged(int activeConnections) {
                metrics.setGauge(MetricNames.WEB_SOCKET_CONNECTIONS_ACTIVE, activeConnections);
            }

            @Override
            public WebSocketObserver openWebSocket() {
                MetricObject object = metrics.openObject(MetricObjectTypes.WEB_SOCKET_CONNECTION);
                return new WebSocketObserver() {
                    @Override
                    public void messageReceived(int payloadBytes) {
                        object.incrementCounter(MetricObjectTypes.WEB_SOCKET_MESSAGES_RECEIVED);
                        object.incrementCounter(MetricObjectTypes.WEB_SOCKET_BYTES_RECEIVED, payloadBytes);
                    }

                    @Override
                    public void messageSent(int payloadBytes) {
                        object.incrementCounter(MetricObjectTypes.WEB_SOCKET_MESSAGES_SENT);
                        object.incrementCounter(MetricObjectTypes.WEB_SOCKET_BYTES_SENT, payloadBytes);
                    }

                    @Override
                    public void close() {
                        object.close();
                    }
                };
            }
        };
    }

    private static int requirePositiveConnectionLimit(int connectionLimit) {
        if (connectionLimit < 1) {
            throw new IllegalArgumentException("connectionLimit must be greater than 0");
        }
        return connectionLimit;
    }

    private static rsp.http.routing.HttpRouter uiRoutes(PageHttpHandler pages,
                                                        Optional<StaticResources> staticResources,
                                                        Optional<StaticResourceHandler> staticResourceHandler) {
        rsp.http.routing.HttpRouter.Builder routes = rsp.http.routing.HttpRouter.builder()
                .get(PageHttpHandler.JS_CLIENT_BUNDLE_PATH,
                        HttpRouteHandler.sync((_, _) -> jsClientBundleResponse()))
                .get("/favicon.ico", HttpRouteHandler.sync((_, _) ->
                        HttpResponses.text(404, "No favicon.ico")));
        if (staticResources.isPresent() && staticResourceHandler.isPresent()) {
            routes.getPrefix(staticResources.get().contextPath(), HttpPrefixHandler.sync((request, _) ->
                    staticResourceHandler.get().handle(request.path())));
        }
        return routes.fallback(pages).build();
    }

    private static void addResultRoutes(rsp.http.routing.HttpRouter.Builder routes,
                                        List<HttpRouter.ResultRegistration> resultRoutes,
                                        PageHttpHandler pages) {
        for (HttpRouter.ResultRegistration registration : resultRoutes) {
            routes.route(registration.method(), registration.template(), (request, route) -> {
                try {
                    HttpResult result = Objects.requireNonNull(
                            registration.handler().handle(request, route), "route result");
                    if (result instanceof HttpResponse response) {
                        return CompletableFuture.completedFuture(response);
                    }
                    if (result instanceof PageResult page) {
                        return pages.handle(request, page);
                    }
                    return CompletableFuture.failedFuture(new IllegalStateException(
                            "Unsupported HTTP result: " + result.getClass().getName()));
                } catch (Throwable failure) {
                    return CompletableFuture.failedFuture(failure);
                }
            }, registration.metadata().toArray(rsp.http.routing.HttpRouteMetadata[]::new));
        }
    }

    private static HttpResponse jsClientBundleResponse() {
        URL resource = WebServer.class.getResource(PageHttpHandler.JS_CLIENT_BUNDLE_PATH);
        if (resource == null) {
            return HttpResponses.status(500);
        }
        return HttpResponse.ok()
                .stream(() -> {
                    try {
                        return resource.openStream();
                    } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                    }
                }, OptionalLong.empty(), MediaType.parse("application/javascript"))
                .build();
    }

}
