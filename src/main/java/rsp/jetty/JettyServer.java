package rsp.jetty;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import rsp.App;
import rsp.javax.web.MainHttpServlet;
import rsp.javax.web.MainWebSocketEndpoint;
import rsp.page.StateToRouteDispatch;
import rsp.server.HttpRequest;
import rsp.server.Path;
import rsp.server.StaticResources;
import rsp.page.PageRendering;

import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * An embedded server for an RSP application,
 * Jetty provides a servlet container and a JSR 356 WebSockets API implementation
 */
public final class JettyServer {
    /**
     * The Jetty server's maximum threads number by default is {@value #DEFAULT_WEB_SERVER_MAX_THREADS}
     */
    public static final int DEFAULT_WEB_SERVER_MAX_THREADS = 50;

    private final int port;
    private final Path basePath;
    private final App app;
    private final Optional<StaticResources> staticResources;
    private final int maxThreads;

    private Server server;

    /**
     * Creates a Jetty web server instance for hosting an RSP application
     * @param port a web server's listening port
     * @param basePath a context path of the web application
     * @param app an RSP application
     * @param staticResources a setup object for an optional static resources handler
     */
    public JettyServer(int port,
                       String basePath,
                       App app,
                       Optional<StaticResources> staticResources,
                       int maxThreads) {
        this.port = port;
        this.basePath = Objects.requireNonNull(Path.of(basePath));
        this.app = Objects.requireNonNull(app);
        this.staticResources = Objects.requireNonNull(staticResources);
        this.maxThreads = maxThreads;
    }

    /**
     * Creates a Jetty web server instance for hosting an RSP application
     * @param port a web server's listening port
     * @param basePath a context path of the web application
     * @param app an RSP application
     * @param staticResources a setup object for an optional static resources handler
     */
    public JettyServer(int port,
                       String basePath,
                       App app,
                       StaticResources staticResources) {
        this(port, basePath, app, Optional.of(staticResources), DEFAULT_WEB_SERVER_MAX_THREADS);
    }

    /**
     * Creates a Jetty web server instance for hosting an RSP application
     * @param port a web server's listening port
     * @param basePath a context path of the web application
     * @param app an RSP application
     */
    public JettyServer(int port, String basePath, App app) {
        this(port, basePath, app, Optional.empty(), DEFAULT_WEB_SERVER_MAX_THREADS);
    }

    /**
     * Starts the server
     * @throws Exception in case when the server's start failed
     */
    public void start() throws Exception {
        final QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMaxThreads(maxThreads);
        
        server = new Server(threadPool);
        
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.setConnectors(new Connector[] {connector});

        final HandlerList handlers = new HandlerList();
        staticResources.ifPresent(sr -> {
            final ResourceHandler resourcesHandler = new ResourceHandler();
            resourcesHandler.setDirectoriesListed(true);
            resourcesHandler.setResourceBase(sr.resourcesBaseDir.getAbsolutePath());
            final ContextHandler resourceContextHandler = new ContextHandler();
            resourceContextHandler.setHandler(resourcesHandler);
            resourceContextHandler.setContextPath(sr.contextPath);
            handlers.addHandler(resourceContextHandler);
        });

        final ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/" + basePath);
        context.addServlet(new ServletHolder(new MainHttpServlet<>(new PageRendering(app.routes,
                                                                                     app.pagesStorage,
                                                                                     app.rootComponent,
                                                                                     app.enrichRenderContext()),
                                                               app.config.log)),"/*");
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(app.config.schedulerThreadPoolSize);
        final MainWebSocketEndpoint webSocketEndpoint =  new MainWebSocketEndpoint<>(app.routes,
                                                                                     new StateToRouteDispatch(basePath, app.state2path),
                                                                                     app.pagesStorage,
                                                                                     app.rootComponent,
                                                                                     app.enrichRenderContext(),
                                                                                     () -> scheduler,
                                                                                     app.listeners,
                                                                                     app.config.log);
        WebSocketServerContainerInitializer.configure(context, (servletContext, serverContainer) -> {
            final ServerEndpointConfig config =
                    ServerEndpointConfig.Builder.create(webSocketEndpoint.getClass(), app.WS_ENDPOINT_PATH)
                                                .configurator(new ServerEndpointConfig.Configurator() {
                                                    @Override
                                                    public <T> T getEndpointInstance(Class<T> clazz) throws InstantiationException {
                                                        return (T)webSocketEndpoint;
                                                    }

                                                    @Override
                                                    public void modifyHandshake(ServerEndpointConfig conf,
                                                                                HandshakeRequest req,
                                                                                HandshakeResponse resp) {
                                                        conf.getUserProperties().put(MainWebSocketEndpoint.HANDSHAKE_REQUEST_PROPERTY_NAME,
                                                                                     HttpRequest.of(req));
                                                    }
                                                }).build();
            serverContainer.addEndpoint(config);
        });
        handlers.addHandler(context);

        server.setHandler(handlers);
        server.start();
        app.config.log.info(l -> l.log("Server started"));
    }

    /**
     * Blocks the current thread while the server's threads are running
     * @throws InterruptedException if any of the server's thread interrupted
     */
    public void join() throws InterruptedException {
        server.join();
    }
}
