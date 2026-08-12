package rsp.http;

import rsp.component.definitions.Component;
import rsp.page.DefaultEventLoop;
import rsp.page.EventLoop;
import rsp.page.HttpHandler;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderedPage;
import rsp.server.SslConfiguration;
import rsp.server.StaticResourceHandler;
import rsp.server.StaticResources;
import rsp.server.http.HttpRequest;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Shell for the zero-runtime-dependency HTTP/WebSocket server that will replace the Jetty adapter.
 * <p>
 * This class intentionally exposes the current {@code WebServer} construction surface while the
 * transport slices are implemented incrementally. Calling {@link #start()} fails clearly until
 * HTTP serving lands in the next slice.
 */
public class WebServer {

    /**
     * Compatibility value retained from the Jetty adapter. Future slices should reinterpret this as
     * a connection/concurrency limit rather than an OS-thread-pool size.
     */
    public static final int DEFAULT_WEB_SERVER_MAX_THREADS = 50;

    /**
     * The default rate of heartbeat messages from a browser to server.
     */
    public static final int DEFAULT_HEARTBEAT_INTERVAL_MS = 10_000;

    /**
     * Rendered pages waiting for their WebSocket session to bind.
     */
    public final Map<QualifiedSessionId, RenderedPage> pagesStorage = new ConcurrentHashMap<>();

    private final int configuredPort;
    private final Function<HttpRequest, Component<?, ?>> rootComponentDefinition;
    private final Optional<StaticResources> staticResources;
    private final Optional<SslConfiguration> sslConfiguration;
    private final int maxThreads;
    private final Supplier<EventLoop> eventLoopSupplier;
    private final Optional<StaticResourceHandler> staticResourceHandler;
    private final HttpHandler httpHandler;

    /**
     * Creates a web server instance for hosting an application.
     *
     * @param port a web server's listening port
     * @param rootComponentDefinition a root component's definition
     * @param staticResources a setup object for an optional static resources handler
     * @param sslConfiguration a TLS connection configuration or {@link Optional#empty()} for HTTP
     * @param maxThreads retained compatibility setting from the Jetty adapter
     * @param eventLoopSupplier creates event loops for live page sessions
     */
    public WebServer(final int port,
                     final Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                     final Optional<StaticResources> staticResources,
                     final Optional<SslConfiguration> sslConfiguration,
                     final int maxThreads,
                     final Supplier<EventLoop> eventLoopSupplier) {
        this.configuredPort = port;
        this.rootComponentDefinition = Objects.requireNonNull(rootComponentDefinition);
        this.staticResources = Objects.requireNonNull(staticResources);
        this.sslConfiguration = Objects.requireNonNull(sslConfiguration);
        this.maxThreads = maxThreads;
        this.eventLoopSupplier = Objects.requireNonNull(eventLoopSupplier);
        this.staticResourceHandler = this.staticResources.map(sr -> new StaticResourceHandler(sr.resourcesBaseDir(),
                                                                                              sr.contextPath()));
        this.httpHandler = new HttpHandler(pagesStorage,
                                           this.rootComponentDefinition,
                                           this.staticResourceHandler,
                                           DEFAULT_HEARTBEAT_INTERVAL_MS);
    }

    public WebServer(final int port,
                     final Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                     final Optional<StaticResources> staticResources,
                     final Optional<SslConfiguration> sslConfiguration,
                     final int maxThreads) {
        this(port, rootComponentDefinition, staticResources, sslConfiguration, maxThreads, DefaultEventLoop::new);
    }

    /**
     * Creates a web server instance for hosting an application.
     *
     * @param port a web server's listening port
     * @param rootComponentDefinition an application's root server component
     * @param staticResources a setup object for an optional static resources handler
     */
    public <S> WebServer(final int port,
                         final Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                         final StaticResources staticResources) {
        this(port, rootComponentDefinition, Optional.of(staticResources), Optional.empty(), DEFAULT_WEB_SERVER_MAX_THREADS);
    }

    /**
     * Creates a web server instance for hosting an application.
     *
     * @param port a web server's listening port
     * @param rootComponentDefinition a root component
     * @param staticResources a setup object for an optional static resources handler
     * @param sslConfiguration the server's TLS configuration
     */
    public WebServer(final int port,
                     final Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                     final StaticResources staticResources,
                     final SslConfiguration sslConfiguration) {
        this(port,
             rootComponentDefinition,
             Optional.of(staticResources),
             Optional.of(sslConfiguration),
             DEFAULT_WEB_SERVER_MAX_THREADS);
    }

    /**
     * Creates a web server instance for hosting an application.
     *
     * @param port a web server's listening port
     * @param rootComponentDefinition a root component
     */
    public WebServer(final int port,
                     final Function<HttpRequest, Component<?, ?>> rootComponentDefinition) {
        this(port, rootComponentDefinition, Optional.empty(), Optional.empty(), DEFAULT_WEB_SERVER_MAX_THREADS);
    }

    /**
     * Starts the server.
     */
    public void start() {
        throw new UnsupportedOperationException("system/http WebServer transport is not implemented yet");
    }

    /**
     * Blocks the current thread while the server is running.
     */
    public void join() {
        throw new UnsupportedOperationException("system/http WebServer transport is not implemented yet");
    }

    /**
     * Stops the server.
     */
    public void stop() {
        throw new UnsupportedOperationException("system/http WebServer transport is not implemented yet");
    }

    /**
     * Returns the configured listening port. Future transport slices should return the actual bound
     * port after {@link #start()}, especially when configured with port {@code 0}.
     *
     * @return the configured port
     */
    public int port() {
        return configuredPort;
    }

    protected Function<HttpRequest, Component<?, ?>> rootComponentDefinition() {
        return rootComponentDefinition;
    }

    protected Optional<StaticResources> staticResources() {
        return staticResources;
    }

    protected Optional<SslConfiguration> sslConfiguration() {
        return sslConfiguration;
    }

    protected int maxThreads() {
        return maxThreads;
    }

    protected Supplier<EventLoop> eventLoopSupplier() {
        return eventLoopSupplier;
    }

    protected Optional<StaticResourceHandler> staticResourceHandler() {
        return staticResourceHandler;
    }

    protected HttpHandler httpHandler() {
        return httpHandler;
    }
}
