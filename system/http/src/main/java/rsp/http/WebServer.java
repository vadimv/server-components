package rsp.http;

import rsp.component.definitions.Component;
import rsp.metrics.MetricNames;
import rsp.metrics.Metrics;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static rsp.util.SafeDiagnostics.failure;

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
    static final int WEB_SOCKET_CLOSE_GRACE_TIMEOUT_MS = 1_000;
    static final String WEB_SOCKET_SERVER_STOP_REASON = "Server stopping";

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
    private final LocalSessionResumeConfig localSessionResumeConfig;
    private final Metrics metrics;
    private final LocalSessionRegistry localSessionRegistry;
    private final Optional<StaticResourceHandler> staticResourceHandler;
    private final HttpHandler httpHandler;
    private final HttpRequestParser requestParser = new HttpRequestParser();
    private final HttpResponseWriter responseWriter = new HttpResponseWriter();
    private final WebSocketUpgrader webSocketUpgrader = new WebSocketUpgrader();
    private final WebSocketEndpoint rspWebSocketEndpoint;
    private final Object lifecycleLock = new Object();
    private final Semaphore connectionPermits;
    private final Set<WebSocketConnection> activeWebSockets = ConcurrentHashMap.newKeySet();

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
        this(port,
             rootComponentDefinition,
             staticResources,
             sslConfiguration,
             connectionLimit,
             eventLoopSupplier,
             LocalSessionResumeConfig.defaults());
    }

    /**
     * Creates a web server with explicit local-session resume bounds.
     *
     * @param port a web server's listening port
     * @param rootComponentDefinition a root component's definition
     * @param staticResources a setup object for an optional static resources handler
     * @param sslConfiguration a TLS connection configuration or {@link Optional#empty()} for HTTP
     * @param connectionLimit maximum number of concurrently handled HTTP connections
     * @param eventLoopSupplier creates event loops for live page sessions
     * @param localSessionResumeConfig same-process WebSocket resume bounds
     */
    public WebServer(final int port,
                     final Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                     final Optional<StaticResources> staticResources,
                     final Optional<SslConfiguration> sslConfiguration,
                     final int connectionLimit,
                     final Supplier<EventLoop> eventLoopSupplier,
                     final LocalSessionResumeConfig localSessionResumeConfig) {
        this(port,
             rootComponentDefinition,
             staticResources,
             sslConfiguration,
             connectionLimit,
             eventLoopSupplier,
             localSessionResumeConfig,
             Metrics.noop());
    }

    /**
     * Creates a web server with explicit session bounds and process-wide metrics.
     *
     * @param port a web server's listening port
     * @param rootComponentDefinition a root component's definition
     * @param staticResources a setup object for an optional static resources handler
     * @param sslConfiguration a TLS connection configuration or {@link Optional#empty()} for HTTP
     * @param connectionLimit maximum number of concurrently handled HTTP connections
     * @param eventLoopSupplier creates event loops for live page sessions
     * @param localSessionResumeConfig same-process WebSocket resume bounds
     * @param metrics process-wide metrics sink
     */
    public WebServer(final int port,
                     final Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                     final Optional<StaticResources> staticResources,
                     final Optional<SslConfiguration> sslConfiguration,
                     final int connectionLimit,
                     final Supplier<EventLoop> eventLoopSupplier,
                     final LocalSessionResumeConfig localSessionResumeConfig,
                     final Metrics metrics) {
        this.configuredPort = port;
        this.rootComponentDefinition = Objects.requireNonNull(rootComponentDefinition);
        this.staticResources = Objects.requireNonNull(staticResources);
        this.sslConfiguration = Objects.requireNonNull(sslConfiguration);
        this.connectionLimit = requirePositiveConnectionLimit(connectionLimit);
        this.eventLoopSupplier = Objects.requireNonNull(eventLoopSupplier);
        this.localSessionResumeConfig = Objects.requireNonNull(localSessionResumeConfig);
        this.metrics = Objects.requireNonNull(metrics);
        this.connectionPermits = new Semaphore(this.connectionLimit);
        this.boundPort = port;
        this.localSessionRegistry = new LocalSessionRegistry(pagesStorage,
                                                             this.eventLoopSupplier,
                                                             this.localSessionResumeConfig,
                                                             this.metrics);
        this.rspWebSocketEndpoint = new RspWebSocketEndpoint(localSessionRegistry);
        this.staticResourceHandler = this.staticResources.map(sr -> new StaticResourceHandler(sr.resourcesBaseDir(),
                                                                                              sr.contextPath()));
        this.httpHandler = new HttpHandler(pagesStorage,
                                           this.rootComponentDefinition,
                                           this.staticResourceHandler,
                                           DEFAULT_HEARTBEAT_INTERVAL_MS,
                                           this.metrics);
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
     * Creates a web server with static resources and a process-wide metrics sink.
     *
     * @param port a web server's listening port
     * @param rootComponentDefinition an application's root server component
     * @param staticResources a setup object for the static resources handler
     * @param metrics process-wide metrics sink
     */
    public WebServer(final int port,
                     final Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                     final StaticResources staticResources,
                     final Metrics metrics) {
        this(port,
             rootComponentDefinition,
             Optional.of(staticResources),
             Optional.empty(),
             DEFAULT_CONNECTION_LIMIT,
             DefaultEventLoop::new,
             LocalSessionResumeConfig.defaults(),
             metrics);
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
     * Creates a web server with a process-wide metrics sink.
     *
     * @param port a web server's listening port
     * @param rootComponentDefinition a root component
     * @param metrics process-wide metrics sink
     */
    public WebServer(final int port,
                     final Function<HttpRequest, Component<?, ?>> rootComponentDefinition,
                     final Metrics metrics) {
        this(port,
             rootComponentDefinition,
             Optional.empty(),
             Optional.empty(),
             DEFAULT_CONNECTION_LIMIT,
             DefaultEventLoop::new,
             LocalSessionResumeConfig.defaults(),
             metrics);
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
                localSessionRegistry.start();
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
        final Thread threadToInterrupt;
        final Set<WebSocketConnection> webSocketsToClose;
        synchronized (lifecycleLock) {
            if (!running
                && serverSocket == null
                && connectionExecutor == null
                && activeWebSockets.isEmpty()
                && localSessionRegistry.size() == 0) {
                return;
            }
            running = false;
            socketToClose = serverSocket;
            executorToClose = connectionExecutor;
            threadToInterrupt = acceptorThread;
            webSocketsToClose = Set.copyOf(activeWebSockets);
            serverSocket = null;
            connectionExecutor = null;
            acceptorThread = null;
        }

        if (socketToClose != null) {
            try {
                socketToClose.close();
            } catch (final IOException ex) {
                logger.log(DEBUG, () -> failure("Server socket close failed", ex));
            }
        }
        if (threadToInterrupt != null) {
            threadToInterrupt.interrupt();
        }
        pagesStorage.clear();
        if (executorToClose != null) {
            executorToClose.shutdown();
        }
        initiateWebSocketShutdown(webSocketsToClose);
        localSessionRegistry.closeAll();
        awaitWebSocketsClosed(webSocketsToClose, WEB_SOCKET_CLOSE_GRACE_TIMEOUT_MS);
        forceCloseWebSockets(webSocketsToClose);
        awaitConnectionExecutor(executorToClose);
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

    protected LocalSessionResumeConfig localSessionResumeConfig() {
        return localSessionResumeConfig;
    }

    protected Metrics metrics() {
        return metrics;
    }

    protected Optional<StaticResourceHandler> staticResourceHandler() {
        return staticResourceHandler;
    }

    protected HttpHandler httpHandler() {
        return httpHandler;
    }

    int activeWebSocketCount() {
        return activeWebSockets.size();
    }

    int liveSessionCount() {
        return localSessionRegistry.size();
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
                currentExecutor.submit(() -> {
                    try {
                        handleConnection(acceptedSocket);
                    } finally {
                        connectionPermits.release();
                    }
                });
                socket = null;
            } catch (final RejectedExecutionException ex) {
                connectionPermits.release();
                closeQuietly(socket);
                if (running) {
                    logger.log(ERROR, () -> failure("HTTP connection rejected by executor", ex));
                }
            } catch (final SocketException ex) {
                if (running) {
                    logger.log(ERROR, () -> failure("Server socket failed", ex));
                }
                closeQuietly(socket);
                return;
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                closeQuietly(socket);
                return;
            } catch (final IOException ex) {
                if (running) {
                    logger.log(ERROR, () -> failure("HTTP connection accept failed", ex));
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
            metrics.incrementCounter(MetricNames.HTTP_REQUESTS);
            if (isWebSocketDispatch(request.request())) {
                handleWebSocket(socket, request);
                return;
            }
            if (!isSupportedHttpMethod(request.method())) {
                metrics.incrementCounter(MetricNames.HTTP_FAILURES);
                responseWriter.write(socket.getOutputStream(),
                                     HttpResponses.status(405),
                                     request.method());
                return;
            }
            final HttpResponse response = httpHandler.handle(request.request()).handle((resp, ex) -> {
                if (ex == null) {
                    return resp;
                }
                logger.log(ERROR, () -> failure("HTTP rendering failed", ex));
                return HttpResponses.status(500);
            }).join();
            if (response.status >= 400) {
                metrics.incrementCounter(MetricNames.HTTP_FAILURES);
            }
            responseWriter.write(socket.getOutputStream(), response, request.method());
        } catch (final HttpProtocolException ex) {
            metrics.incrementCounter(MetricNames.HTTP_FAILURES);
            writeProtocolError(socket, ex);
        } catch (final IOException ex) {
            metrics.incrementCounter(MetricNames.HTTP_FAILURES);
            logger.log(DEBUG, () -> failure("HTTP connection closed with I/O error", ex));
        } catch (final RuntimeException ex) {
            metrics.incrementCounter(MetricNames.HTTP_FAILURES);
            logger.log(ERROR, () -> failure("Unexpected HTTP connection failure", ex));
            writeRuntimeError(socket);
        }
    }

    private void handleWebSocket(final Socket socket, final ParsedHttpRequest request) throws IOException {
        try {
            final WebSocketEndpoint endpoint = webSocketEndpoint(request.request())
                    .orElseThrow(() -> new WebSocketHandshakeException(404, "WebSocket endpoint not found"));
            endpoint.validate(request.request());
            webSocketUpgrader.upgrade(socket, request, endpoint.supportedSubprotocols());
            final WebSocketSession session = new WebSocketSession(socket);
            final WebSocketConnection connection = new WebSocketConnection(socket,
                                                                           session,
                                                                           endpoint.open(request.request(), session));
            final boolean shouldRun = registerWebSocket(connection);
            try {
                if (!shouldRun) {
                    connection.initiateClose(WebSocketFrame.CLOSE_GOING_AWAY, WEB_SOCKET_SERVER_STOP_REASON);
                    connection.forceClose();
                    return;
                }
                connection.run();
            } finally {
                deregisterWebSocket(connection);
            }
        } catch (final WebSocketHandshakeException ex) {
            metrics.incrementCounter(MetricNames.HTTP_FAILURES);
            responseWriter.write(socket.getOutputStream(), HttpResponses.status(ex.status()), request.method());
        }
    }

    private boolean registerWebSocket(final WebSocketConnection connection) {
        synchronized (lifecycleLock) {
            activeWebSockets.add(connection);
            updateWebSocketGauge();
            return running;
        }
    }

    private void deregisterWebSocket(final WebSocketConnection connection) {
        synchronized (lifecycleLock) {
            if (activeWebSockets.remove(connection)) {
                updateWebSocketGauge();
            }
        }
    }

    /** Must be called while holding {@link #lifecycleLock}. */
    private void updateWebSocketGauge() {
        metrics.setGauge(MetricNames.WEB_SOCKET_CONNECTIONS_ACTIVE, activeWebSockets.size());
    }

    private void initiateWebSocketShutdown(final Set<WebSocketConnection> connections) {
        connections.forEach(connection ->
                connection.initiateClose(WebSocketFrame.CLOSE_GOING_AWAY, WEB_SOCKET_SERVER_STOP_REASON));
    }

    private void forceCloseWebSockets(final Set<WebSocketConnection> connections) {
        connections.stream()
                .filter(connection -> !connection.closed().isDone())
                .forEach(WebSocketConnection::forceClose);
    }

    private void awaitWebSocketsClosed(final Set<WebSocketConnection> connections,
                                       final int timeoutMs) {
        if (connections.isEmpty()) {
            return;
        }
        final CompletableFuture<?>[] closed = connections.stream()
                .map(WebSocketConnection::closed)
                .toArray(CompletableFuture<?>[]::new);
        try {
            CompletableFuture.allOf(closed).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException ex) {
            logger.log(DEBUG, () -> "Timed out waiting for WebSocket close handshakes");
        } catch (final ExecutionException ex) {
            logger.log(DEBUG, () -> failure("WebSocket close waiter failed", ex));
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitConnectionExecutor(final ExecutorService executor) {
        if (executor == null) {
            return;
        }
        try {
            if (!executor.awaitTermination(WEB_SOCKET_CLOSE_GRACE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (final InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private boolean isWebSocketDispatch(final HttpRequest request) {
        return webSocketUpgrader.isWebSocketRequest(request) || webSocketEndpoint(request).isPresent();
    }

    private Optional<WebSocketEndpoint> webSocketEndpoint(final HttpRequest request) {
        if (rspWebSocketEndpoint.matches(request)) {
            return Optional.of(rspWebSocketEndpoint);
        }
        return Optional.empty();
    }

    private void writeProtocolError(final Socket socket, final HttpProtocolException ex) {
        try {
            responseWriter.write(socket.getOutputStream(), HttpResponses.status(ex.status()), null);
        } catch (final IOException ioEx) {
            logger.log(DEBUG, () -> failure("HTTP protocol error response write failed", ioEx));
        }
    }

    private void writeRuntimeError(final Socket socket) {
        try {
            responseWriter.write(socket.getOutputStream(),
                                 HttpResponses.status(500),
                                 null);
        } catch (final IOException ioEx) {
            logger.log(DEBUG, () -> failure("HTTP runtime error response write failed", ioEx));
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
