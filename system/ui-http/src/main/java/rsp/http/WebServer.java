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
import rsp.server.jdk.JdkServerObserver;
import rsp.server.jdk.JdkWebServer;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/** UI page/session adapter backed by the UI-independent JDK HTTP/WebSocket transport. */
public class WebServer implements ApplicationLifecycle {
    public static final int DEFAULT_CONNECTION_LIMIT = JdkWebServer.DEFAULT_CONNECTION_LIMIT;
    public static final int DEFAULT_HEARTBEAT_INTERVAL_MS = 10_000;
    static final int WEB_SOCKET_CLOSE_GRACE_TIMEOUT_MS = JdkWebServer.WEB_SOCKET_CLOSE_GRACE_TIMEOUT_MS;
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
    private final JdkWebServer transport;

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
        this.httpHandler = new PageHttpHandler(pagesStorage, this.pageApplication, this.staticResourceHandler,
                DEFAULT_HEARTBEAT_INTERVAL_MS, this.metrics);
        this.transport = new JdkWebServer(port, httpHandler,
                java.util.List.of(new RspWebSocketEndpoint(localSessionRegistry)), connectionLimit,
                DEFAULT_HEARTBEAT_INTERVAL_MS * 3, transportObserver(this.metrics));
    }

    public static WebServer pages(int port, PageApplication pageApplication) {
        return new WebServer(port, pageApplication, Optional.empty(), Optional.empty(), DEFAULT_CONNECTION_LIMIT,
                DefaultEventLoop::new, LocalSessionResumeConfig.defaults(), Metrics.noop());
    }

    public static WebServer pages(int port, PageApplication pageApplication, StaticResources staticResources) {
        return new WebServer(port, pageApplication, Optional.of(staticResources), Optional.empty(),
                DEFAULT_CONNECTION_LIMIT, DefaultEventLoop::new, LocalSessionResumeConfig.defaults(),
                Metrics.noop());
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
            throw new UnsupportedOperationException("TLS is not implemented in server-jdk yet");
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

    int activeWebSocketCount() {
        return transport.activeWebSocketCount();
    }

    int liveSessionCount() {
        return localSessionRegistry.size();
    }

    private static JdkServerObserver transportObserver(Metrics metrics) {
        return new JdkServerObserver() {
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
}
