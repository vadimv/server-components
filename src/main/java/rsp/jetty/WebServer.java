package rsp.jetty;

import org.eclipse.jetty.ee10.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.pathmap.PathSpec;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.PathMappingsHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import rsp.component.definitions.StatefulComponentDefinition;
import rsp.javax.web.MainHttpServlet;
import rsp.javax.web.MainWebSocketEndpoint;
import rsp.javax.web.HttpRequestUtils;
import rsp.page.*;
import rsp.server.SslConfiguration;
import rsp.server.StaticResources;
import rsp.server.http.HttpRequest;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import jakarta.websocket.server.ServerEndpointConfig.Configurator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.lang.System.Logger.Level.INFO;

/**
 * An embedded server for an RSP application,
 * Jetty provides a servlet container and a JSR 356 WebSockets API implementation.
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
     * Creates a web server instance for hosting an RSP application.
     * @param port a web server's listening port
     * @param rootComponentDefinition a root component's definition
     * @param sslConfiguration an TLS connection configuration or {@link Optional#empty()} for HTTP
     * @param staticResources a setup object for an optional static resources handler
     */
    public <S> WebServer(final int port,
                         final Function<HttpRequest, StatefulComponentDefinition<S>> rootComponentDefinition,
                         final Optional<StaticResources> staticResources,
                         final Optional<SslConfiguration> sslConfiguration,
                         final int maxThreads) {
        this.port = port;
        Objects.requireNonNull(rootComponentDefinition);

        final QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMaxThreads(maxThreads);

        server = new Server(threadPool);

        sslConfiguration.ifPresentOrElse(ssl -> {
                    final HttpConfiguration https = new HttpConfiguration();
                    https.addCustomizer(new SecureRequestCustomizer());

                    final SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
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

        // See a Jetty server example at:
        // https://github.com/jetty/jetty-examples/blob/12.0.x/embedded/path-mapping-handler/src/main/java/examples/PathMappingServer.java
        final PathMappingsHandler mappingsHandler = new PathMappingsHandler();
        staticResources.ifPresent(sr -> {
            final ResourceFactory resourceFactory = ResourceFactory.of(server);
            final Path staticResourcesDirectory = sr.resourcesBaseDir().toPath();

            if (!Files.exists(staticResourcesDirectory) || !Files.isDirectory(staticResourcesDirectory)) {
                throw new RuntimeException("Unable to find static files directory: " + staticResourcesDirectory.toAbsolutePath());
            }
            if (!Files.isReadable(staticResourcesDirectory)) {
                throw new RuntimeException( "Unable to read static files directory: " + staticResourcesDirectory.toAbsolutePath());
            }

            final Resource staticResourcesDirectoryResource = resourceFactory.newResource(staticResourcesDirectory);
            final ResourceHandler staticResourcesDirectoryResourceHandler = new ResourceHandler();
            staticResourcesDirectoryResourceHandler.setBaseResource(staticResourcesDirectoryResource);
            staticResourcesDirectoryResourceHandler.setDirAllowed(true);
            mappingsHandler.addMapping(PathSpec.from(sr.contextPath()),
                                       new StripContextPath(stripTrailingWildcardSymbols(sr.contextPath()),
                                                            staticResourcesDirectoryResourceHandler));
        });

        final ServletContextHandler servletContextHandler = new ServletContextHandler();
        servletContextHandler.setContextPath("/");
        servletContextHandler.addServlet(new ServletHolder(new MainHttpServlet<>(new PageRendering<>(pagesStorage,
                                                                                 rootComponentDefinition,
                                                                                 DEFAULT_HEARTBEAT_INTERVAL_MS))),
                                         "/*");
        final MainWebSocketEndpoint webSocketEndpoint = new MainWebSocketEndpoint(pagesStorage);
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
        mappingsHandler.addMapping(PathSpec.from("/"), servletContextHandler);

        server.setHandler(mappingsHandler);
    }

    private static String stripTrailingWildcardSymbols(String path) {
        return path.replaceAll("\\*+$", "").replaceAll("/+$", "");
    }

    private static final class StripContextPath extends PathNameWrapper
    {
        public StripContextPath(String contextPath, Handler handler)
        {
            super(path -> path.startsWith(contextPath) ? path.substring(contextPath.length()) : path, handler);
        }
    }

    private static class PathNameWrapper extends Handler.Wrapper
    {
        private final Function<String, String> nameFunction;

        public PathNameWrapper(Function<String, String> nameFunction, Handler handler)
        {
            super(handler);
            this.nameFunction = nameFunction;
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception
        {
            final String originalPath = request.getHttpURI().getPath();
            final String newPath = nameFunction.apply(originalPath);
            final HttpURI newURI = HttpURI.build(request.getHttpURI()).path(newPath);

            final Request wrappedRequest = new Request.Wrapper(request)
            {
                @Override
                public HttpURI getHttpURI()
                {
                    return newURI;
                }
            };
            return super.handle(wrappedRequest, response, callback);
        }
    }

    /**
     * Creates a Jetty web server instance for hosting an RSP application.
     * @param port a web server's listening port
     * @param rootComponentDefinition a root component
     * @param staticResources a setup object for an optional static resources handler
     */
    public <S> WebServer(final int port,
                         final Function<HttpRequest, StatefulComponentDefinition<S>> rootComponentDefinition,
                         final StaticResources staticResources) {
        this(port, rootComponentDefinition, Optional.of(staticResources), Optional.empty(), DEFAULT_WEB_SERVER_MAX_THREADS);
    }

    /**
     * Creates a Jetty web server instance for hosting an RSP application.
     * @param port a web server's listening port
     * @param rootComponentDefinition a root component
     * @param staticResources a setup object for an optional static resources handler
     * @param sslConfiguration the server's TLS configuration
     */
    public <S> WebServer(final int port,
                     final Function<HttpRequest, StatefulComponentDefinition<S>> rootComponentDefinition,
                     final StaticResources staticResources,
                     final SslConfiguration sslConfiguration) {
        this(port, rootComponentDefinition, Optional.of(staticResources), Optional.of(sslConfiguration), DEFAULT_WEB_SERVER_MAX_THREADS);
    }

    /**
     * Creates a Jetty web server instance for hosting an RSP application.
     * @param port a web server's listening port
     * @param rootComponentDefinition a root component
     */
    public <S> WebServer(final int port,
                         final Function<HttpRequest, StatefulComponentDefinition<S>> rootComponentDefinition) {
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
