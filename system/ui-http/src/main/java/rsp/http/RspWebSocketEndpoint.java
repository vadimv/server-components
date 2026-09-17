package rsp.http;

import rsp.page.QualifiedSessionId;
import rsp.websocket.WebSocketEndpoint;
import rsp.websocket.WebSocketHandshakeException;
import rsp.websocket.WebSocketListener;
import rsp.websocket.WebSocketProtocolException;
import rsp.websocket.WebSocketSession;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;
import static rsp.util.SafeDiagnostics.failure;
import static rsp.websocket.WebSocketCloseCodes.INVALID_PAYLOAD;
import static rsp.websocket.WebSocketCloseCodes.MESSAGE_TOO_BIG;
import static rsp.websocket.WebSocketCloseCodes.PROTOCOL_ERROR;
import static rsp.websocket.WebSocketCloseCodes.UNSUPPORTED_DATA;

final class RspWebSocketEndpoint implements WebSocketEndpoint {
    private static final String ENDPOINT_PREFIX = "/bridge/web-socket";

    private final LocalSessionRegistry liveSessions;

    RspWebSocketEndpoint(final LocalSessionRegistry liveSessions) {
        this.liveSessions = Objects.requireNonNull(liveSessions);
    }

    @Override
    public boolean matches(final HttpRequest request) {
        return request.path().toString().equals(ENDPOINT_PREFIX)
               || request.path().toString().startsWith(ENDPOINT_PREFIX + "/");
    }

    @Override
    public void validate(final HttpRequest request) throws WebSocketHandshakeException {
        if (sessionId(request).isEmpty()) {
            throw new WebSocketHandshakeException(404, "WebSocket endpoint not found");
        }
    }

    @Override
    public WebSocketListener open(final HttpRequest request, final WebSocketSession session) {
        return new RspWebSocketListener(session,
                                        sessionId(request).orElseThrow(),
                                        liveSessions);
    }

    private Optional<QualifiedSessionId> sessionId(final HttpRequest request) {
        if (request.path().elementsCount() != 4
            || !"bridge".equals(request.path().get(0))
            || !"web-socket".equals(request.path().get(1))) {
            return Optional.empty();
        }
        return Optional.of(new QualifiedSessionId(request.path().get(2), request.path().get(3)));
    }

    private static final class RspWebSocketListener implements WebSocketListener {
        private static final System.Logger logger = System.getLogger(RspWebSocketListener.class.getName());

        private final WebSocketSession socket;
        private final QualifiedSessionId sessionId;
        private final LocalSessionRegistry liveSessions;
        private final ResumablePageSession.Transport transport;

        private ResumablePageSession pageSession;
        private ResumablePageSession.AttachmentHandle attachmentHandle;

        private RspWebSocketListener(final WebSocketSession socket,
                                     final QualifiedSessionId sessionId,
                                     final LocalSessionRegistry liveSessions) {
            this.socket = Objects.requireNonNull(socket);
            this.sessionId = Objects.requireNonNull(sessionId);
            this.liveSessions = Objects.requireNonNull(liveSessions);
            this.transport = new ResumablePageSession.Transport() {
                @Override
                public boolean isOpen() {
                    return socket.isOpen();
                }

                @Override
                public void sendText(final String text) throws IOException {
                    RspWebSocketListener.this.sendText(text);
                }

                @Override
                public void close(final int code, final String reason) {
                    try {
                        socket.close(code, reason);
                    } catch (final IOException ex) {
                        logger.log(DEBUG, () -> failure("WebSocket close failed", ex));
                        socket.abort();
                    }
                }

                @Override
                public void closeSocket() {
                    socket.abort();
                }
            };
        }

        @Override
        public void onOpen() {
            pageSession = liveSessions.findOrCreate(sessionId).orElse(null);
            if (pageSession == null) {
                logger.log(WARNING, () -> "Local page session not found; client reload required");
                sendControl(RspTransportProtocol.resumeRejected("session-not-found"));
            }
        }

        @Override
        public void onText(final String message) throws WebSocketProtocolException {
            logger.log(TRACE, () -> "RSP message received [chars=" + message.length() + "]");
            if (pageSession == null) {
                return;
            }

            final Optional<RspTransportProtocol.ClientControl> control =
                    RspTransportProtocol.decodeClientControl(message);
            if (attachmentHandle == null) {
                if (control.isEmpty() || !(control.get() instanceof RspTransportProtocol.Resume resume)) {
                    throw new WebSocketProtocolException(PROTOCOL_ERROR,
                                                         "The first RSP message must be RESUME");
                }
                resume(resume);
                return;
            }

            if (control.isEmpty()) {
                pageSession.acceptApplicationMessage(attachmentHandle, message);
            } else {
                switch (control.get()) {
                    case RspTransportProtocol.Acknowledge acknowledge ->
                            pageSession.acknowledge(attachmentHandle, acknowledge.sequence());
                    case RspTransportProtocol.Terminate _ ->
                            pageSession.terminate(attachmentHandle, "client-terminated");
                    case RspTransportProtocol.Resume _ ->
                            throw new WebSocketProtocolException(PROTOCOL_ERROR,
                                                                 "RSP connection is already resumed");
                }
            }
        }

        @Override
        public void onBinary(final byte[] payload) throws IOException {
            if (pageSession != null && attachmentHandle != null) {
                pageSession.terminate(attachmentHandle,
                                      "binary-protocol-message",
                                      UNSUPPORTED_DATA);
            } else {
                socket.close(UNSUPPORTED_DATA, "Binary RSP protocol is not supported yet");
            }
        }

        @Override
        public void onClose(final int code, final String reason) {
            if (pageSession == null || attachmentHandle == null) {
                return;
            }
            if (isFatalProtocolClose(code)) {
                pageSession.terminate(attachmentHandle, "protocol-error", code);
            } else {
                pageSession.detach(attachmentHandle);
            }
        }

        @Override
        public void onError(final Throwable throwable) {
            if (pageSession != null && attachmentHandle != null) {
                pageSession.detach(attachmentHandle);
            }
        }

        private void resume(final RspTransportProtocol.Resume resume) {
            if (resume.protocolVersion() != RspTransportProtocol.VERSION) {
                rejectResume("protocol-version-mismatch");
                return;
            }
            final ResumablePageSession.AttachResult result =
                    pageSession.attach(transport, resume.lastAppliedSequence());
            if (result.accepted()) {
                attachmentHandle = result.handle();
            } else {
                rejectResume(result.rejectionReason());
            }
        }

        private void rejectResume(final String reason) {
            sendControl(RspTransportProtocol.resumeRejected(reason));
            try {
                socket.close(PROTOCOL_ERROR, reason);
            } catch (final IOException ex) {
                logger.log(DEBUG, () -> failure("WebSocket resume rejection failed", ex));
                socket.abort();
            }
        }

        private void sendControl(final String message) {
            try {
                sendText(message);
            } catch (final IOException ex) {
                logger.log(DEBUG, () -> failure("RSP transport control send failed", ex));
                socket.abort();
            }
        }

        private void sendText(final String text) throws IOException {
            if (!socket.isOpen()) {
                throw new IOException("WebSocket is closed");
            }
            logger.log(TRACE, () -> "RSP message sent [chars=" + text.length() + "]");
            socket.sendText(text);
        }

        private static boolean isFatalProtocolClose(final int code) {
            return code == PROTOCOL_ERROR
                   || code == UNSUPPORTED_DATA
                   || code == INVALID_PAYLOAD
                   || code == MESSAGE_TOO_BIG;
        }
    }
}
