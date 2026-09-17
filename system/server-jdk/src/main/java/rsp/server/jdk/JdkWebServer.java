package rsp.server.jdk;

import rsp.http.HttpApplication;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.websocket.WebSocketCloseCodes;
import rsp.websocket.WebSocketEndpoint;
import rsp.websocket.WebSocketHandshakeException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;

/**
 * JDK-socket HTTP/1.1 and RFC 6455 transport for UI-independent applications.
 *
 * <p>Each accepted HTTP connection serves one request and is then closed. Request bodies and
 * assembled WebSocket messages are bounded at 256 KiB. TLS, keep-alive, pipelining, and chunked
 * request decoding are intentionally outside this transport.</p>
 */
public final class JdkWebServer {
    public static final int DEFAULT_CONNECTION_LIMIT = 50;
    public static final int DEFAULT_WEB_SOCKET_READ_TIMEOUT_MS = 30_000;
    public static final int WEB_SOCKET_CLOSE_GRACE_TIMEOUT_MS = 1_000;

    private static final String SERVER_STOP_REASON = "Server stopping";
    private static final System.Logger logger = System.getLogger(JdkWebServer.class.getName());

    private final int configuredPort;
    private final HttpApplication application;
    private final List<WebSocketEndpoint> webSocketEndpoints;
    private final int connectionLimit;
    private final int webSocketReadTimeoutMs;
    private final JdkServerObserver observer;
    private final HttpRequestParser requestParser = new HttpRequestParser();
    private final HttpResponseWriter responseWriter = new HttpResponseWriter();
    private final WebSocketUpgrader webSocketUpgrader = new WebSocketUpgrader();
    private final Object lifecycleLock = new Object();
    private final Semaphore connectionPermits;
    private final Set<WebSocketConnection> activeWebSockets = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private volatile ServerSocket serverSocket;
    private volatile ExecutorService connectionExecutor;
    private volatile Thread acceptorThread;
    private volatile boolean running;
    private volatile int boundPort;

    public JdkWebServer(int port, HttpApplication application) {
        this(port, application, List.of(), DEFAULT_CONNECTION_LIMIT,
                DEFAULT_WEB_SOCKET_READ_TIMEOUT_MS, JdkServerObserver.NOOP);
    }

    public JdkWebServer(int port, HttpApplication application, List<? extends WebSocketEndpoint> endpoints) {
        this(port, application, endpoints, DEFAULT_CONNECTION_LIMIT,
                DEFAULT_WEB_SOCKET_READ_TIMEOUT_MS, JdkServerObserver.NOOP);
    }

    public JdkWebServer(int port,
                        HttpApplication application,
                        List<? extends WebSocketEndpoint> endpoints,
                        int connectionLimit,
                        int webSocketReadTimeoutMs,
                        JdkServerObserver observer) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (connectionLimit < 1) {
            throw new IllegalArgumentException("connectionLimit must be greater than 0");
        }
        if (webSocketReadTimeoutMs < 1) {
            throw new IllegalArgumentException("webSocketReadTimeoutMs must be greater than 0");
        }
        this.configuredPort = port;
        this.application = Objects.requireNonNull(application, "application");
        this.webSocketEndpoints = List.copyOf(Objects.requireNonNull(endpoints, "endpoints"));
        this.connectionLimit = connectionLimit;
        this.webSocketReadTimeoutMs = webSocketReadTimeoutMs;
        this.observer = Objects.requireNonNull(observer, "observer");
        this.connectionPermits = new Semaphore(connectionLimit);
        this.boundPort = port;
    }

    public void start() {
        synchronized (lifecycleLock) {
            if (running) {
                throw new IllegalStateException("JdkWebServer is already running");
            }
            ServerSocket socket = null;
            boolean applicationStarted = false;
            try {
                application.start();
                applicationStarted = true;
                socket = new ServerSocket();
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(configuredPort));
                serverSocket = socket;
                boundPort = socket.getLocalPort();
                connectionExecutor = Executors.newVirtualThreadPerTaskExecutor();
                running = true;
                acceptorThread = Thread.startVirtualThread(this::acceptLoop);
            } catch (IOException failure) {
                closeQuietly(socket);
                RuntimeException result = new RuntimeException(failure);
                stopAfterFailedStart(applicationStarted, result);
                throw result;
            } catch (RuntimeException | Error failure) {
                closeQuietly(socket);
                stopAfterFailedStart(applicationStarted, failure);
                throw failure;
            }
        }
        logger.log(INFO, () -> "Server started, listening on port: " + boundPort);
    }

    public void join() {
        Thread thread = acceptorThread;
        if (thread == null) {
            return;
        }
        try {
            thread.join();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(failure);
        }
    }

    public void stop() {
        ServerSocket socketToClose;
        ExecutorService executorToClose;
        Thread threadToInterrupt;
        Set<WebSocketConnection> webSocketsToClose;
        synchronized (lifecycleLock) {
            if (!running && serverSocket == null && connectionExecutor == null && activeWebSockets.isEmpty()) {
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

        closeQuietly(socketToClose);
        if (threadToInterrupt != null) {
            threadToInterrupt.interrupt();
        }
        if (executorToClose != null) {
            executorToClose.shutdown();
        }
        webSocketsToClose.forEach(connection ->
                connection.initiateClose(WebSocketCloseCodes.GOING_AWAY, SERVER_STOP_REASON));
        awaitWebSocketsClosed(webSocketsToClose);
        webSocketsToClose.stream().filter(connection -> !connection.closed().isDone())
                .forEach(WebSocketConnection::forceClose);
        awaitExecutor(executorToClose);
        application.stop();
    }

    /** Returns the configured port before start and the bound port afterwards. */
    public int port() {
        return boundPort;
    }

    public boolean isRunning() {
        return running;
    }

    public int activeWebSocketCount() {
        return activeWebSockets.size();
    }

    public int connectionLimit() {
        return connectionLimit;
    }

    private void acceptLoop() {
        while (running) {
            Socket accepted = null;
            boolean acquired = false;
            try {
                ServerSocket currentSocket = serverSocket;
                ExecutorService currentExecutor = connectionExecutor;
                if (currentSocket == null || currentExecutor == null) {
                    return;
                }
                accepted = currentSocket.accept();
                connectionPermits.acquire();
                acquired = true;
                Socket connection = accepted;
                currentExecutor.submit(() -> {
                    try {
                        handleConnection(connection);
                    } finally {
                        connectionPermits.release();
                    }
                });
                accepted = null;
                acquired = false;
            } catch (RejectedExecutionException failure) {
                if (acquired) {
                    connectionPermits.release();
                }
                closeQuietly(accepted);
                if (running) {
                    logger.log(ERROR, "HTTP connection rejected by executor", failure);
                }
            } catch (SocketException failure) {
                closeQuietly(accepted);
                if (running) {
                    logger.log(ERROR, "Server socket failed", failure);
                }
                return;
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                closeQuietly(accepted);
                return;
            } catch (IOException failure) {
                closeQuietly(accepted);
                if (running) {
                    logger.log(ERROR, "HTTP connection accept failed", failure);
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        try (socket) {
            socket.setSoTimeout(HttpRequestParser.HEADER_READ_TIMEOUT_MS);
            Optional<ParsedHttpRequest> parsed = requestParser.parse(socket, "http");
            if (parsed.isEmpty()) {
                return;
            }
            ParsedHttpRequest request = parsed.get();
            observer.requestReceived();
            Optional<WebSocketEndpoint> endpoint = webSocketEndpoint(request.request());
            if (webSocketUpgrader.isWebSocketRequest(request.request()) || endpoint.isPresent()) {
                handleWebSocket(socket, request, endpoint);
                return;
            }
            HttpResponse response;
            try {
                response = application.handle(request.request()).handle((value, failure) -> {
                    if (failure == null) {
                        return value;
                    }
                    logApplicationFailure(failure);
                    return HttpResponses.status(500);
                }).toCompletableFuture().join();
            } catch (RuntimeException failure) {
                logApplicationFailure(failure);
                response = HttpResponses.status(500);
            }
            if (response.status().code() >= 400) {
                observer.requestFailed();
            }
            responseWriter.write(socket.getOutputStream(), response, request.method());
        } catch (HttpProtocolException failure) {
            observer.requestFailed();
            writeError(socket, failure.status());
        } catch (IOException failure) {
            observer.requestFailed();
            logger.log(DEBUG, "HTTP connection closed with I/O error", failure);
        } catch (RuntimeException failure) {
            observer.requestFailed();
            logger.log(ERROR, "Unexpected HTTP connection failure", failure);
            writeError(socket, 500);
        }
    }

    private void handleWebSocket(Socket socket,
                                 ParsedHttpRequest request,
                                 Optional<WebSocketEndpoint> selectedEndpoint) throws IOException {
        try {
            WebSocketEndpoint endpoint = selectedEndpoint
                    .orElseThrow(() -> new WebSocketHandshakeException(404, "WebSocket endpoint not found"));
            endpoint.validate(request.request());
            webSocketUpgrader.upgrade(socket, request, endpoint.supportedSubprotocols());
            JdkServerObserver.WebSocketObserver connectionObserver =
                    Objects.requireNonNull(observer.openWebSocket(), "WebSocket observer");
            try (connectionObserver) {
                JdkWebSocketSession session = new JdkWebSocketSession(socket, connectionObserver);
                WebSocketConnection connection = new WebSocketConnection(socket, session,
                        endpoint.open(request.request(), session), connectionObserver, webSocketReadTimeoutMs);
                boolean shouldRun = registerWebSocket(connection);
                try {
                    if (!shouldRun) {
                        connection.initiateClose(WebSocketCloseCodes.GOING_AWAY, SERVER_STOP_REASON);
                        connection.forceClose();
                        return;
                    }
                    connection.run();
                } finally {
                    deregisterWebSocket(connection);
                }
            }
        } catch (WebSocketHandshakeException failure) {
            observer.requestFailed();
            responseWriter.write(socket.getOutputStream(), HttpResponses.status(failure.status().code()),
                    request.method());
        }
    }

    private Optional<WebSocketEndpoint> webSocketEndpoint(HttpRequest request) {
        return webSocketEndpoints.stream().filter(endpoint -> endpoint.matches(request)).findFirst();
    }

    private boolean registerWebSocket(WebSocketConnection connection) {
        synchronized (lifecycleLock) {
            activeWebSockets.add(connection);
            observer.activeWebSocketsChanged(activeWebSockets.size());
            return running;
        }
    }

    private void deregisterWebSocket(WebSocketConnection connection) {
        synchronized (lifecycleLock) {
            if (activeWebSockets.remove(connection)) {
                observer.activeWebSocketsChanged(activeWebSockets.size());
            }
        }
    }

    private void writeError(Socket socket, int status) {
        try {
            responseWriter.write(socket.getOutputStream(), HttpResponses.status(status), null);
        } catch (IOException failure) {
            logger.log(DEBUG, "HTTP error response write failed", failure);
        }
    }

    private void awaitWebSocketsClosed(Set<WebSocketConnection> connections) {
        if (connections.isEmpty()) {
            return;
        }
        CompletableFuture<?>[] futures = connections.stream().map(WebSocketConnection::closed)
                .toArray(CompletableFuture<?>[]::new);
        try {
            CompletableFuture.allOf(futures).get(WEB_SOCKET_CLOSE_GRACE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException failure) {
            logger.log(DEBUG, "Timed out waiting for WebSocket close handshakes");
        } catch (ExecutionException failure) {
            logger.log(DEBUG, "WebSocket close waiter failed", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitExecutor(ExecutorService executor) {
        if (executor == null) {
            return;
        }
        try {
            if (!executor.awaitTermination(WEB_SOCKET_CLOSE_GRACE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException failure) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(ServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Best effort during shutdown.
            }
        }
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Best effort during accept-loop failures.
            }
        }
    }

    private void stopAfterFailedStart(boolean applicationStarted, Throwable failure) {
        if (!applicationStarted) {
            return;
        }
        try {
            application.stop();
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static void logApplicationFailure(Throwable failure) {
        logger.log(ERROR, () -> "HTTP application failed [type=" + failure.getClass().getName() + "]");
    }
}
