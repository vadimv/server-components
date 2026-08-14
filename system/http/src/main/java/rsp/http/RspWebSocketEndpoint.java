package rsp.http;

import rsp.page.EventLoop;
import rsp.page.LivePageSession;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderedPage;
import rsp.page.events.InitSessionCommand;
import rsp.page.events.ShutdownSessionCommand;
import rsp.server.RemoteOut;
import rsp.server.http.HttpRequest;
import rsp.server.protocol.RemotePageMessageDecoder;
import rsp.server.protocol.RemotePageMessageEncoder;
import rsp.util.json.JsonUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;

final class RspWebSocketEndpoint implements WebSocketEndpoint {
    private static final String ENDPOINT_PREFIX = "/bridge/web-socket";

    private final Map<QualifiedSessionId, RenderedPage> renderedPages;
    private final Supplier<EventLoop> eventLoopSupplier;

    RspWebSocketEndpoint(final Map<QualifiedSessionId, RenderedPage> renderedPages,
                         final Supplier<EventLoop> eventLoopSupplier) {
        this.renderedPages = Objects.requireNonNull(renderedPages);
        this.eventLoopSupplier = Objects.requireNonNull(eventLoopSupplier);
    }

    @Override
    public boolean matches(final HttpRequest request) {
        return request.path.toString().equals(ENDPOINT_PREFIX)
               || request.path.toString().startsWith(ENDPOINT_PREFIX + "/");
    }

    @Override
    public void validate(final HttpRequest request) throws WebSocketHandshakeException {
        if (sessionId(request).isEmpty()) {
            throw new WebSocketHandshakeException(404, "WebSocket endpoint not found");
        }
    }

    @Override
    public WebSocketListener open(final HttpRequest request, final WebSocketSession session) {
        return new RspWebSocketListener(request,
                                        session,
                                        sessionId(request).orElseThrow(),
                                        renderedPages,
                                        eventLoopSupplier);
    }

    private Optional<QualifiedSessionId> sessionId(final HttpRequest request) {
        if (request.path.elementsCount() != 4
            || !"bridge".equals(request.path.get(0))
            || !"web-socket".equals(request.path.get(1))) {
            return Optional.empty();
        }
        return Optional.of(new QualifiedSessionId(request.path.get(2), request.path.get(3)));
    }

    private static final class RspWebSocketListener implements WebSocketListener {
        private static final System.Logger logger = System.getLogger(RspWebSocketListener.class.getName());
        private static final Set<QualifiedSessionId> lostSessionsIds = Collections.newSetFromMap(new WeakHashMap<>());

        private final HttpRequest handshakeRequest;
        private final WebSocketSession session;
        private final QualifiedSessionId sessionId;
        private final Map<QualifiedSessionId, RenderedPage> renderedPages;
        private final Supplier<EventLoop> eventLoopSupplier;
        private final AtomicBoolean shutdownSent = new AtomicBoolean();

        private LivePageSession livePage;
        private RemotePageMessageDecoder decoder;

        private RspWebSocketListener(final HttpRequest handshakeRequest,
                                     final WebSocketSession session,
                                     final QualifiedSessionId sessionId,
                                     final Map<QualifiedSessionId, RenderedPage> renderedPages,
                                     final Supplier<EventLoop> eventLoopSupplier) {
            this.handshakeRequest = Objects.requireNonNull(handshakeRequest);
            this.session = Objects.requireNonNull(session);
            this.sessionId = Objects.requireNonNull(sessionId);
            this.renderedPages = Objects.requireNonNull(renderedPages);
            this.eventLoopSupplier = Objects.requireNonNull(eventLoopSupplier);
        }

        @Override
        public void onOpen() {
            final RemoteOut remoteOut = new RemotePageMessageEncoder(this::sendText);
            final RenderedPage renderedPage = renderedPages.remove(sessionId);
            if (renderedPage == null) {
                logger.log(TRACE, () -> "Pre-rendered page not found for SID: " + sessionId);
                if (!isKnownLostSession(sessionId)) {
                    logger.log(WARNING, () -> "Reload a remote on: " + handshakeRequest.url);
                    remoteOut.evalJs(-1, "RSP.reload()");
                }
                return;
            }

            livePage = new LivePageSession(eventLoopSupplier.get());
            livePage.eventsConsumer().accept(new InitSessionCommand(renderedPage.pageBuilder(),
                                                                    renderedPage.commandsEnqueue(),
                                                                    remoteOut));
            decoder = new RemotePageMessageDecoder(JsonUtils.createParser(), livePage.eventsConsumer());
            remoteOut.setRenderNum(0);
            livePage.start();
            logger.log(DEBUG, () -> "Live page session started: " + sessionId);
        }

        @Override
        public void onText(final String message) {
            logger.log(TRACE, () -> sessionId + " -> " + message);
            if (decoder != null) {
                decoder.decode(message);
            }
        }

        @Override
        public void onBinary(final byte[] payload) throws IOException {
            session.close(WebSocketFrame.CLOSE_UNSUPPORTED_DATA, "Binary RSP protocol is not supported yet");
        }

        @Override
        public void onClose(final int code, final String reason) {
            shutdownSession();
        }

        @Override
        public void onError(final Throwable throwable) {
            shutdownSession();
        }

        private void sendText(final String text) {
            if (!session.isOpen()) {
                return;
            }
            try {
                logger.log(TRACE, () -> sessionId + " <- " + text);
                session.sendText(text);
            } catch (final IOException ex) {
                logger.log(DEBUG, "WebSocket write failed", ex);
                session.closeSocket();
            }
        }

        private void shutdownSession() {
            if (livePage != null && shutdownSent.compareAndSet(false, true)) {
                livePage.eventsConsumer().accept(new ShutdownSessionCommand());
                logger.log(DEBUG, () -> "Shutdown session: " + sessionId);
            }
        }

        private static boolean isKnownLostSession(final QualifiedSessionId sessionId) {
            synchronized (lostSessionsIds) {
                if (lostSessionsIds.contains(sessionId)) {
                    return true;
                }
                lostSessionsIds.add(sessionId);
                return false;
            }
        }
    }
}
