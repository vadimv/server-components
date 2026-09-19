package rsp.http;

import rsp.component.definitions.Component;
import rsp.application.ApplicationLifecycle;
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

    private final PageApplication pageApplication;
    private final Optional<StaticResources> staticResources;
    private final Optional<SslConfiguration> sslConfiguration;
    private final int connectionLimit;
    private final Supplier<EventLoop> eventLoopSupplier;
    private final LocalSessionResumeConfig localSessionResumeConfig;
    private final Metrics metrics;
    private final LocalSessionRegistry localSessionRegistry;
    private final Optional<StaticResourceHandler> staticResourceHandler;
    private final PageHttpHandler httpHandler;
    private final HttpApplication httpApplication;
    private final SocketWebServer transport;

    public WebServer(int port,
                     Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                     Optional<StaticResources> staticResources,
                     Optional<SslConfiguration> sslConfiguration,
                     int connectionLimit,
                     Supplier<EventLoop> eventLoopSupplier) {
        this(port, rootComponentDefinition, staticResources, sslConfiguration, connectionLimit,
                eventLoopSupplier, LocalSessionResumeConfig.defaults());
    }

    public WebServer(int port,
                     Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                     Optional<StaticResources> staticResources,
                     Optional<SslConfiguration> sslConfiguration,
                     int connectionLimit,
                     Supplier<EventLoop> eventLoopSupplier,
                     LocalSessionResumeConfig localSessionResumeConfig) {
        this(port, rootComponentDefinition, staticResources, sslConfiguration, connectionLimit,
                eventLoopSupplier, localSessionResumeConfig, Metrics.noop());
    }

    public WebServer(int port,
                     Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                     Optional<StaticResources> staticResources,
                     Optional<SslConfiguration> sslConfiguration,
                     int connectionLimit,
                     Supplier<EventLoop> eventLoopSupplier,
                     LocalSessionResumeConfig localSessionResumeConfig,
                     Metrics metrics) {
        this(port, Pages.live(rootComponentDefinition), staticResources, sslConfiguration, connectionLimit,
                eventLoopSupplier, localSessionResumeConfig, metrics);
    }

    public WebServer(int port,
                     PageApplication pageApplication,
                     Optional<StaticResources> staticResources,
                     Optional<SslConfiguration> sslConfiguration,
                     int connectionLimit,
                     Supplier<EventLoop> eventLoopSupplier,
                     LocalSessionResumeConfig localSessionResumeConfig,
                     Metrics metrics) {
        this(port, pageApplication, staticResources, sslConfiguration, connectionLimit, eventLoopSupplier,
                localSessionResumeConfig, metrics, rsp.http.routing.HttpRouter.builder().build(), List.of(), List.of());
    }

    private WebServer(int port,
                      PageApplication pageApplication,
                      Optional<StaticResources> staticResources,
                      Optional<SslConfiguration> sslConfiguration,
                      int connectionLimit,
                      Supplier<EventLoop> eventLoopSupplier,
                      LocalSessionResumeConfig localSessionResumeConfig,
                      Metrics metrics,
                      rsp.http.routing.HttpRouter applicationRoutes,
                      List<HttpRouter.ResultRegistration> resultRoutes,
                      List<? extends HttpMiddleware> middleware) {
        this.pageApplication = Objects.requireNonNull(pageApplication, "pageApplication");
        this.staticResources = Objects.requireNonNull(staticResources, "staticResources");
        this.sslConfiguration = Objects.requireNonNull(sslConfiguration, "sslConfiguration");
        this.connectionLimit = requirePositiveConnectionLimit(connectionLimit);
        this.eventLoopSupplier = Objects.requireNonNull(eventLoopSupplier, "eventLoopSupplier");
        this.localSessionResumeConfig = Objects.requireNonNull(localSessionResumeConfig, "localSessionResumeConfig");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.localSessionRegistry = new LocalSessionRegistry(pagesStorage, this.eventLoopSupplier,
                this.localSessionResumeConfig, this.metrics);
        this.staticResourceHandler = this.staticResources.map(resources ->
                new StaticResourceHandler(resources.resourcesBaseDir(), resources.contextPath()));
        this.httpHandler = new PageHttpHandler(pagesStorage, this.pageApplication,
                DEFAULT_HEARTBEAT_INTERVAL_MS, this.metrics);
        rsp.http.routing.HttpRouter.Builder routesBuilder = rsp.http.routing.HttpRouter.builder()
                .include(Objects.requireNonNull(applicationRoutes, "applicationRoutes"));
        addResultRoutes(routesBuilder, Objects.requireNonNull(resultRoutes, "resultRoutes"), httpHandler);
        HttpApplication routes = routesBuilder
                .fallback(uiRoutes(httpHandler, this.staticResources, this.staticResourceHandler))
                .build();
        this.httpApplication = HttpMiddleware.pipeline(routes,
                Objects.requireNonNull(middleware, "middleware"));
        this.transport = new SocketWebServer(port, httpApplication,
                java.util.List.of(new RspWebSocketEndpoint(localSessionRegistry)), connectionLimit,
                DEFAULT_HEARTBEAT_INTERVAL_MS * 3, transportObserver(this.metrics));
    }

    /** Starts fluent assembly of a UI server with optional generic HTTP routes. */
    public static Builder builder(int port, PageApplication pageApplication) {
        return new Builder(port, pageApplication);
    }

    /** Starts fluent assembly of a server whose endpoints are supplied through {@link Builder#routes(Router)}. */
    public static Builder builder(int port) {
        return new Builder(port, _ -> Pages.response(HttpResponse.status(HttpStatus.NOT_FOUND).build()));
    }

    /** Starts fluent assembly with a mixed graph of HTTP and UI page routes. */
    public static Builder builder(int port, Router router) {
        return builder(port).routes(router);
    }

    public static WebServer pages(int port, PageApplication pageApplication) {
        return builder(port, pageApplication).build();
    }

    public static WebServer pages(int port, PageApplication pageApplication, StaticResources staticResources) {
        return builder(port, pageApplication).staticResources(staticResources).build();
    }

    public WebServer(int port,
                     Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                     Optional<StaticResources> staticResources,
                     Optional<SslConfiguration> sslConfiguration,
                     int connectionLimit) {
        this(port, rootComponentDefinition, staticResources, sslConfiguration, connectionLimit,
                DefaultEventLoop::new);
    }

    public WebServer(int port,
                     Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                     StaticResources staticResources) {
        this(port, rootComponentDefinition, Optional.of(staticResources), Optional.empty(),
                DEFAULT_CONNECTION_LIMIT);
    }

    public WebServer(int port,
                     Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                     StaticResources staticResources,
                     Metrics metrics) {
        this(port, rootComponentDefinition, Optional.of(staticResources), Optional.empty(),
                DEFAULT_CONNECTION_LIMIT, DefaultEventLoop::new, LocalSessionResumeConfig.defaults(), metrics);
    }

    public WebServer(int port,
                     Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                     StaticResources staticResources,
                     SslConfiguration sslConfiguration) {
        this(port, rootComponentDefinition, Optional.of(staticResources), Optional.of(sslConfiguration),
                DEFAULT_CONNECTION_LIMIT);
    }

    public WebServer(int port, Function<HttpRequest, Component<?, ?>> rootComponentDefinition) {
        this(port, rootComponentDefinition, Optional.empty(), Optional.empty(), DEFAULT_CONNECTION_LIMIT);
    }

    public WebServer(int port,
                     Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                     Metrics metrics) {
        this(port, rootComponentDefinition, Optional.empty(), Optional.empty(), DEFAULT_CONNECTION_LIMIT,
                DefaultEventLoop::new, LocalSessionResumeConfig.defaults(), metrics);
    }

    public synchronized void start() {
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
        transport.join();
    }

    public synchronized void stop() {
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

    public int port() {
        return transport.port();
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
        return staticResourceHandler;
    }

    protected PageHttpHandler httpHandler() {
        return httpHandler;
    }

    protected HttpApplication httpApplication() {
        return httpApplication;
    }

    int activeWebSocketCount() {
        return transport.activeWebSocketCount();
    }

    int liveSessionCount() {
        return localSessionRegistry.size();
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

    /** Mutable configuration DSL. The built server and its route graph are immutable. */
    public static final class Builder {
        private final int port;
        private final PageApplication pageApplication;
        private Optional<StaticResources> staticResources = Optional.empty();
        private Optional<SslConfiguration> sslConfiguration = Optional.empty();
        private int connectionLimit = DEFAULT_CONNECTION_LIMIT;
        private Supplier<EventLoop> eventLoopSupplier = DefaultEventLoop::new;
        private LocalSessionResumeConfig localSessionResumeConfig = LocalSessionResumeConfig.defaults();
        private Metrics metrics = Metrics.noop();
        private final rsp.http.routing.HttpRouter.Builder routes = rsp.http.routing.HttpRouter.builder();
        private final List<HttpRouter.ResultRegistration> resultRoutes = new ArrayList<>();
        private final List<HttpMiddleware> middleware = new ArrayList<>();

        private Builder(int port, PageApplication pageApplication) {
            this.port = port;
            this.pageApplication = Objects.requireNonNull(pageApplication, "pageApplication");
        }

        /** Adds routes handled before framework assets and UI page fallback. */
        public Builder routes(rsp.http.routing.HttpRouter routes) {
            this.routes.include(Objects.requireNonNull(routes, "routes"));
            return this;
        }

        /** Adds ordinary HTTP endpoints and UI pages to one method-aware route graph. */
        public Builder routes(Router router) {
            HttpRouter routes = (HttpRouter) Objects.requireNonNull(router, "router");
            this.routes.include(routes.responseRoutes());
            resultRoutes.addAll(routes.resultRoutes());
            return this;
        }

        public Builder staticResources(StaticResources staticResources) {
            this.staticResources = Optional.of(Objects.requireNonNull(staticResources, "staticResources"));
            return this;
        }

        public Builder ssl(SslConfiguration sslConfiguration) {
            this.sslConfiguration = Optional.of(Objects.requireNonNull(sslConfiguration, "sslConfiguration"));
            return this;
        }

        public Builder connectionLimit(int connectionLimit) {
            this.connectionLimit = requirePositiveConnectionLimit(connectionLimit);
            return this;
        }

        public Builder eventLoops(Supplier<EventLoop> eventLoopSupplier) {
            this.eventLoopSupplier = Objects.requireNonNull(eventLoopSupplier, "eventLoopSupplier");
            return this;
        }

        public Builder localSessionResume(LocalSessionResumeConfig config) {
            this.localSessionResumeConfig = Objects.requireNonNull(config, "config");
            return this;
        }

        public Builder metrics(Metrics metrics) {
            this.metrics = Objects.requireNonNull(metrics, "metrics");
            return this;
        }

        /** Adds cross-cutting behavior around application, framework, and page routes. */
        public Builder middleware(HttpMiddleware value) {
            middleware.add(Objects.requireNonNull(value, "value"));
            return this;
        }

        public WebServer build() {
            return new WebServer(port, pageApplication, staticResources, sslConfiguration, connectionLimit,
                    eventLoopSupplier, localSessionResumeConfig, metrics, routes.build(), resultRoutes, middleware);
        }
    }
}
