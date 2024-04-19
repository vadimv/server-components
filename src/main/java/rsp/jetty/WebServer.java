package rsp.jetty;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import rsp.App;
import rsp.javax.web.MainHttpServlet;
import rsp.javax.web.MainWebSocketEndpoint;
import rsp.javax.web.HttpRequestUtils;
import rsp.page.*;
import rsp.server.SslConfiguration;
import rsp.server.StaticResources;

import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.server.ServerEndpointConfig.Configurator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.lang.System.Logger.Level.INFO;

/**
 * An embedded server for an RSP application,
 * Jetty provides a servlet container and a JSR 356 WebSockets API implementation.
 */
public final class WebServer<S> {
    private static final System.Logger logger = System.getLogger(WebServer.class.getName());

    /**
     * The Jetty server's maximum threads number by default is {@value #DEFAULT_WEB_SERVER_MAX_THREADS}.
     */
    public static final int DEFAULT_WEB_SERVER_MAX_THREADS = 50;

    private final int port;
    private final Server server;

    /**
     * Creates a web server instance for hosting an RSP application.
     * @param port a web server's listening port
     * @param app an RSP application
     * @param sslConfiguration an TLS connection configuration or {@link Optional#empty()} for HTTP
     * @param staticResources a setup object for an optional static resources handler
     */
    public WebServer(final int port,
                     final App<S> app,
                     final Optional<StaticResources> staticResources,
                     final Optional<SslConfiguration> sslConfiguration,
                     final int maxThreads) {
        this.port = port;
        Objects.requireNonNull(app);

        final QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMaxThreads(maxThreads);

        server = new Server(threadPool);

        sslConfiguration.ifPresentOrElse(ssl -> {
                    final HttpConfiguration https = new HttpConfiguration();
                    https.addCustomizer(new SecureRequestCustomizer());

                    final SslContextFactory sslContextFactory = new SslContextFactory.Server();
                    sslContextFactory.setKeyStorePath(ssl.keyStorePath);
                    sslContextFactory.setKeyStorePassword(ssl.keyStorePassword);
                    sslContextFactory.setKeyManagerPassword(ssl.keyStorePassword);

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

        final HandlerList handlers = new HandlerList();
        staticResources.ifPresent(sr -> {
            final ResourceHandler resourcesHandler = new ResourceHandler();
            resourcesHandler.setDirectoriesListed(true);
            resourcesHandler.setResourceBase(sr.resourcesBaseDir().getAbsolutePath());
            final ContextHandler resourceContextHandler = new ContextHandler();
            resourceContextHandler.setHandler(resourcesHandler);
            resourceContextHandler.setContextPath(sr.contextPath());
            handlers.addHandler(resourceContextHandler);
        });

        final ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(new MainHttpServlet<>(new PageRendering<>(app.pagesStorage,
                                                                                       app.rootComponentDefinition,
                                                                                       app.config.heartbeatIntervalMs))),
                          "/*");
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(app.config.schedulerThreadPoolSize);
        final MainWebSocketEndpoint<S> webSocketEndpoint = new MainWebSocketEndpoint<>(app.pagesStorage,
                                                                                       () -> scheduler);
        WebSocketServerContainerInitializer.configure(context, (servletContext, serverContainer) -> {
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
        handlers.addHandler(context);

        server.setHandler(handlers);
    }

    /**
     * Creates a Jetty web server instance for hosting an RSP application.
     * @param port a web server's listening port
     * @param app an RSP application
     * @param staticResources a setup object for an optional static resources handler
     */
    public WebServer(final int port,
                     final App<S> app,
                     final StaticResources staticResources) {
        this(port, app, Optional.of(staticResources), Optional.empty(), DEFAULT_WEB_SERVER_MAX_THREADS);
    }

    /**
     * Creates a Jetty web server instance for hosting an RSP application.
     * @param port a web server's listening port
     * @param app an RSP application
     * @param staticResources a setup object for an optional static resources handler
     * @param sslConfiguration the server's TLS configuration
     */
    public WebServer(final int port,
                     final App<S> app,
                     final StaticResources staticResources,
                     final SslConfiguration sslConfiguration) {
        this(port, app, Optional.of(staticResources), Optional.of(sslConfiguration), DEFAULT_WEB_SERVER_MAX_THREADS);
    }

    /**
     * Creates a Jetty web server instance for hosting an RSP application.
     * @param port a web server's listening port
     * @param app an RSP application
     */
    public WebServer(final int port, final App<S> app) {
        this(port, app, Optional.empty(), Optional.empty(), DEFAULT_WEB_SERVER_MAX_THREADS);
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
