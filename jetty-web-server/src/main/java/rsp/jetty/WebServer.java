package rsp.jetty;

import org.eclipse.jetty.ee10.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import rsp.component.definitions.Component;
import rsp.javax.web.MainHttpServlet;
import rsp.javax.web.MainWebSocketEndpoint;
import rsp.javax.web.HttpRequestUtils;
import rsp.page.*;
import rsp.server.SslConfiguration;
import rsp.server.StaticResourceHandler;
import rsp.server.StaticResources;
import rsp.server.http.HttpRequest;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import jakarta.websocket.server.ServerEndpointConfig.Configurator;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.System.Logger.Level.INFO;

/**
 * An embedded web server to run inside an application process.
 * It serves dynamic HTML pages, static resources and supports WebSocket connections for live pages sessions.
 * This implementation uses Jetty server, which provides a servlet container and a JSR 356 WebSockets API.
 * @see MainHttpServlet
 * @see MainWebSocketEndpoint
 */
public final class WebServer {
    private static final System.Logger logger = System.getLogger(WebServer.class.getName());

    /**
     * The Jetty server's maximum threads number by default is {@value #DEFAULT_WEB_SERVER_MAX_THREADS}.
     */
    public static final int DEFAULT_WEB_SERVER_MAX_THREADS = 50;

    /**
     * The default rate of heartbeat messages from a browser to server.
     */
    public static final int DEFAULT_HEARTBEAT_INTERVAL_MS = 10_000;

    public final Map<QualifiedSessionId, RenderedPage> pagesStorage = new ConcurrentHashMap<>();

    private final int port;
    private final Server server;

    /**
     * Creates a web server instance for hosting an application.
     * @param port a web server's listening port
     * @param rootComponentDefinition a root component's definition
     * @param sslConfiguration an TLS connection configuration or {@link Optional#empty()} for HTTP
     * @param staticResources a setup object for an optional static resources handler
     */
    public WebServer(final int port,
                     final Function<HttpRequest, Component<?>> rootComponentDefinition,
                     final Optional<StaticResources> staticResources,
                     final Optional<SslConfiguration> sslConfiguration,
                     final int maxThreads,
                     final Supplier<EventLoop> eventLoopSupplier) { // new parameter
        this.port = port;
        Objects.requireNonNull(rootComponentDefinition);

        final QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMaxThreads(maxThreads);

        server = new Server(threadPool);

        sslConfiguration.ifPresentOrElse(ssl -> {
                    final HttpConfiguration https = new HttpConfiguration();
                    https.addCustomizer(new SecureRequestCustomizer());

                    final SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
                    sslContextFactory.setKeyStorePath(ssl.keyStorePath());
                    sslContextFactory.setKeyStorePassword(ssl.keyStorePassword());
                    sslContextFactory.setKeyManagerPassword(ssl.keyStorePassword());

                    final ServerConnector sslConnector = new ServerConnector(server,
                                                                             new SslConnectionFactory(sslContextFactory, "http/1.1"),
                                                                             new HttpConnectionFactory(https));
                    sslConnector.setPort(port);
                    server.setConnectors(new Connector[] { sslConnector });
                },
                () -> {
                    final ServerConnector connector = new ServerConnector(server);
                    connector.setPort(port);
                    server.setConnectors(new Connector[] { connector });
                });

        final Optional<StaticResourceHandler> staticResourceHandler =
                staticResources.map(sr -> new StaticResourceHandler(sr.resourcesBaseDir(),
                                                                                  sr.contextPath()));

        final ServletContextHandler servletContextHandler = new ServletContextHandler();
        servletContextHandler.setContextPath("/");
        servletContextHandler.addServlet(new ServletHolder(new MainHttpServlet(new HttpHandler(pagesStorage,
                                                                                               rootComponentDefinition,
                                                                                               staticResourceHandler,
                                                                                               DEFAULT_HEARTBEAT_INTERVAL_MS))),
                                         "/*");
        final MainWebSocketEndpoint webSocketEndpoint = new MainWebSocketEndpoint(pagesStorage, eventLoopSupplier);
        JakartaWebSocketServletContainerInitializer.configure(servletContextHandler, (servletContext, serverContainer) -> {
            final ServerEndpointConfig config =
                    ServerEndpointConfig.Builder.create(webSocketEndpoint.getClass(), MainWebSocketEndpoint.WS_ENDPOINT_PATH)
                            .configurator(new Configurator() {
                                @Override
                                public <T> T getEndpointInstance(final Class<T> clazz) throws InstantiationException {
                                    if (clazz.equals(MainWebSocketEndpoint.class)) {
                                        @SuppressWarnings("unchecked")
                                        final T endpoint = (T) webSocketEndpoint;
                                        return endpoint;
                                    }
                                    throw new InstantiationException("Expected class " + MainWebSocketEndpoint.class
                                                                     + " got " + clazz);
                                }

                                @Override
                                public void modifyHandshake(final ServerEndpointConfig conf,
                                                            final HandshakeRequest req,
                                                            final HandshakeResponse resp) {
                                    conf.getUserProperties().put(MainWebSocketEndpoint.HANDSHAKE_REQUEST_PROPERTY_NAME,
                                                                 HttpRequestUtils.httpRequest(req));
                                }
                            }).build();
            serverContainer.addEndpoint(config);
        });
        server.setHandler(servletContextHandler);
    }

    public WebServer(final int port,
                     final Function<HttpRequest, Component<?>> rootComponentDefinition,
                     final Optional<StaticResources> staticResources,
                     final Optional<SslConfiguration> sslConfiguration,
                     final int maxThreads) {
        this(port, rootComponentDefinition, staticResources, sslConfiguration, maxThreads, DefaultEventLoop::new);
    }

    /**
     * Creates a web server instance for hosting an application.
     * @param port a web server's listening port
     * @param rootComponentDefinition an application's root server component
     * @param staticResources a setup object for an optional static resources handler
     */
    public <S> WebServer(final int port,
                         final Function<HttpRequest, Component<?>> rootComponentDefinition,
                         final StaticResources staticResources) {
        this(port, rootComponentDefinition, Optional.of(staticResources), Optional.empty(), DEFAULT_WEB_SERVER_MAX_THREADS);
    }

    /**
     * Creates a web server instance for hosting an application.
     * @param port a web server's listening port
     * @param rootComponentDefinition a root component
     * @param staticResources a setup object for an optional static resources handler
     * @param sslConfiguration the server's TLS configuration
     */
    public WebServer(final int port,
                     final Function<HttpRequest, Component<?>> rootComponentDefinition,
                     final StaticResources staticResources,
                     final SslConfiguration sslConfiguration) {
        this(port, rootComponentDefinition, Optional.of(staticResources), Optional.of(sslConfiguration), DEFAULT_WEB_SERVER_MAX_THREADS);
    }

    /**
     * Creates a web server instance for hosting an application.
     * @param port a web server's listening port
     * @param rootComponentDefinition a root component
     */
    public WebServer(final int port,
                         final Function<HttpRequest, Component<?>> rootComponentDefinition) {
        this(port, rootComponentDefinition, Optional.empty(), Optional.empty(), DEFAULT_WEB_SERVER_MAX_THREADS);
    }

    /**
     * Starts the server.
     */
    public void start() {
        try {
            server.start();
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }
        logger.log(INFO, () -> "Server started, listening on port: " + port);
    }

    /**
     * Blocks the current thread while the server's threads are running.
     */
    public void join() {
        try {
            server.join();
        } catch (final InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Stops the server.
     */
    public void stop() {
        try {
            server.stop();
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
