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
import rsp.server.http.HttpResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;

/**
 * Zero-runtime-dependency HTTP server for RSP applications.
 * <p>
 * This slice serves regular HTTP requests through {@link HttpHandler}. WebSocket and TLS transport
 * support are intentionally left for later slices.
 */
public class WebServer {
    private static final System.Logger logger = System.getLogger(WebServer.class.getName());

    /**
     * The default number of concurrently handled HTTP connections.
     */
    public static final int DEFAULT_CONNECTION_LIMIT = 50;

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
    private final int connectionLimit;
    private final Supplier<EventLoop> eventLoopSupplier;
    private final Optional<StaticResourceHandler> staticResourceHandler;
    private final HttpHandler httpHandler;
    private final HttpRequestParser requestParser = new HttpRequestParser();
    private final HttpResponseWriter responseWriter = new HttpResponseWriter();
    private final Object lifecycleLock = new Object();
    private final Semaphore connectionPermits;

    private volatile ServerSocket serverSocket;
    private volatile ExecutorService connectionExecutor;
    private volatile Thread acceptorThread;
    private volatile boolean running;
    private volatile int boundPort;

    /**
     * Creates a web server instance for hosting an application.
     *
     * @param port a web server's listening port
     * @param rootComponentDefinition a root component's definition
     * @param staticResources a setup object for an optional static resources handler
     * @param sslConfiguration a TLS connection configuration or {@link Optional#empty()} for HTTP
     * @param connectionLimit maximum number of concurrently handled HTTP connections
     * @param eventLoopSupplier creates event loops for live page sessions
     */
    public WebServer(final int port,
                     final Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                     final Optional<StaticResources> staticResources,
                     final Optional<SslConfiguration> sslConfiguration,
                     final int connectionLimit,
                     final Supplier<EventLoop> eventLoopSupplier) {
        this.configuredPort = port;
        this.rootComponentDefinition = Objects.requireNonNull(rootComponentDefinition);
        this.staticResources = Objects.requireNonNull(staticResources);
        this.sslConfiguration = Objects.requireNonNull(sslConfiguration);
        this.connectionLimit = requirePositiveConnectionLimit(connectionLimit);
        this.eventLoopSupplier = Objects.requireNonNull(eventLoopSupplier);
        this.connectionPermits = new Semaphore(this.connectionLimit);
        this.boundPort = port;
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
                     final int connectionLimit) {
        this(port, rootComponentDefinition, staticResources, sslConfiguration, connectionLimit, DefaultEventLoop::new);
    }

    /**
     * Creates a web server instance for hosting an application.
     *
     * @param port a web server's listening port
     * @param rootComponentDefinition an application's root server component
     * @param staticResources a setup object for an optional static resources handler
     */
    public WebServer(final int port,
                     final Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                     final StaticResources staticResources) {
        this(port, rootComponentDefinition, Optional.of(staticResources), Optional.empty(), DEFAULT_CONNECTION_LIMIT);
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
             DEFAULT_CONNECTION_LIMIT);
    }

    /**
     * Creates a web server instance for hosting an application.
     *
     * @param port a web server's listening port
     * @param rootComponentDefinition a root component
     */
    public WebServer(final int port,
                     final Function<HttpRequest, Component<?, ?>> rootComponentDefinition) {
        this(port, rootComponentDefinition, Optional.empty(), Optional.empty(), DEFAULT_CONNECTION_LIMIT);
    }

    /**
     * Starts the server.
     */
    public void start() {
        if (sslConfiguration.isPresent()) {
            throw new UnsupportedOperationException("TLS is not implemented in system/http yet");
        }

        synchronized (lifecycleLock) {
            if (running) {
                throw new IllegalStateException("WebServer is already running");
            }
            try {
                final ServerSocket newServerSocket = new ServerSocket();
                newServerSocket.setReuseAddress(true);
                newServerSocket.bind(new InetSocketAddress(configuredPort));
                serverSocket = newServerSocket;
                boundPort = newServerSocket.getLocalPort();
                connectionExecutor = Executors.newVirtualThreadPerTaskExecutor();
                running = true;
                acceptorThread = Thread.startVirtualThread(this::acceptLoop);
            } catch (final IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        logger.log(INFO, () -> "Server started, listening on port: " + boundPort);
    }

    /**
     * Blocks the current thread while the server is running.
     */
    public void join() {
        final Thread thread = acceptorThread;
        if (thread == null) {
            return;
        }
        try {
            thread.join();
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }
    }

    /**
     * Stops the server.
     */
    public void stop() {
        final ServerSocket socketToClose;
        final ExecutorService executorToClose;
        synchronized (lifecycleLock) {
            if (!running && serverSocket == null) {
                return;
            }
            running = false;
            socketToClose = serverSocket;
            executorToClose = connectionExecutor;
            serverSocket = null;
            connectionExecutor = null;
        }

        if (socketToClose != null) {
            try {
                socketToClose.close();
            } catch (final IOException ex) {
                logger.log(DEBUG, "Error closing server socket", ex);
            }
        }
        final Thread thread = acceptorThread;
        if (thread != null) {
            thread.interrupt();
        }
        if (executorToClose != null) {
            executorToClose.shutdownNow();
        }
    }

    /**
     * Returns the configured listening port before start and the actual bound port after start.
     *
     * @return the configured or bound port
     */
    public int port() {
        return boundPort;
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

    protected int connectionLimit() {
        return connectionLimit;
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

    private void acceptLoop() {
        while (running) {
            Socket socket = null;
            try {
                final ServerSocket currentServerSocket = serverSocket;
                final ExecutorService currentExecutor = connectionExecutor;
                if (currentServerSocket == null || currentExecutor == null) {
                    return;
                }
                socket = currentServerSocket.accept();
                connectionPermits.acquire();
                final Socket acceptedSocket = socket;
                socket = null;
                currentExecutor.submit(() -> {
                    try {
                        handleConnection(acceptedSocket);
                    } finally {
                        connectionPermits.release();
                    }
                });
            } catch (final SocketException ex) {
                if (running) {
                    logger.log(ERROR, "Server socket failed", ex);
                }
                closeQuietly(socket);
                return;
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                closeQuietly(socket);
                return;
            } catch (final IOException ex) {
                if (running) {
                    logger.log(ERROR, "Error accepting HTTP connection", ex);
                }
                closeQuietly(socket);
            }
        }
    }

    private void handleConnection(final Socket socket) {
        try (socket) {
            socket.setSoTimeout(HttpRequestParser.HEADER_READ_TIMEOUT_MS);
            final Optional<ParsedHttpRequest> parsedRequest = requestParser.parse(socket, "http");
            if (parsedRequest.isEmpty()) {
                return;
            }
            final ParsedHttpRequest request = parsedRequest.get();
            if (!isSupportedHttpMethod(request.method())) {
                responseWriter.write(socket.getOutputStream(),
                                     HttpResponses.text(405, "Method Not Allowed"),
                                     request.method());
                return;
            }
            if (request.isWebSocketUpgrade()) {
                responseWriter.write(socket.getOutputStream(),
                                     HttpResponses.text(501, "WebSocket transport is not implemented yet"),
                                     request.method());
                return;
            }
            final HttpResponse response = httpHandler.handle(request.request()).handle((resp, ex) -> {
                if (ex == null) {
                    return resp;
                }
                logger.log(ERROR, "HTTP rendering exception", ex);
                return HttpResponses.text(500, "500 Internal server error\nException: " + ex.getMessage());
            }).join();
            responseWriter.write(socket.getOutputStream(), response, request.method());
        } catch (final HttpProtocolException ex) {
            writeProtocolError(socket, ex);
        } catch (final IOException ex) {
            logger.log(DEBUG, "HTTP connection closed with I/O error", ex);
        } catch (final RuntimeException ex) {
            logger.log(ERROR, "Unexpected HTTP connection error", ex);
            writeRuntimeError(socket, ex);
        }
    }

    private void writeProtocolError(final Socket socket, final HttpProtocolException ex) {
        try {
            responseWriter.write(socket.getOutputStream(), HttpResponses.text(ex.status(), ex.getMessage()), null);
        } catch (final IOException ioEx) {
            logger.log(DEBUG, "Failed to write HTTP protocol error", ioEx);
        }
    }

    private void writeRuntimeError(final Socket socket, final RuntimeException ex) {
        try {
            responseWriter.write(socket.getOutputStream(),
                                 HttpResponses.text(500, "500 Internal server error\nException: " + ex.getMessage()),
                                 null);
        } catch (final IOException ioEx) {
            logger.log(DEBUG, "Failed to write HTTP runtime error", ioEx);
        }
    }

    private static void closeQuietly(final Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (final IOException ignored) {
                // Best effort cleanup during accept-loop failures.
            }
        }
    }

    private static boolean isSupportedHttpMethod(final rsp.server.http.HttpMethod method) {
        return method == rsp.server.http.HttpMethod.GET
               || method == rsp.server.http.HttpMethod.HEAD
               || method == rsp.server.http.HttpMethod.POST;
    }

    private static int requirePositiveConnectionLimit(final int connectionLimit) {
        if (connectionLimit < 1) {
            throw new IllegalArgumentException("connectionLimit must be greater than 0");
        }
        return connectionLimit;
    }
}
