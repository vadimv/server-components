package rsp.http;

import rsp.page.QualifiedSessionId;
import rsp.server.http.HttpRequest;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;

final class RspWebSocketEndpoint implements WebSocketEndpoint {
    private static final String ENDPOINT_PREFIX = "/bridge/web-socket";

    private final LocalSessionRegistry liveSessions;

    RspWebSocketEndpoint(final LocalSessionRegistry liveSessions) {
        this.liveSessions = Objects.requireNonNull(liveSessions);
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
                                        liveSessions);
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

        private final HttpRequest handshakeRequest;
        private final WebSocketSession socket;
        private final QualifiedSessionId sessionId;
        private final LocalSessionRegistry liveSessions;
        private final ResumablePageSession.Transport transport;

        private ResumablePageSession pageSession;
        private ResumablePageSession.AttachmentHandle attachmentHandle;

        private RspWebSocketListener(final HttpRequest handshakeRequest,
                                     final WebSocketSession socket,
                                     final QualifiedSessionId sessionId,
                                     final LocalSessionRegistry liveSessions) {
            this.handshakeRequest = Objects.requireNonNull(handshakeRequest);
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
                        logger.log(DEBUG, "Failed to close WebSocket for " + sessionId, ex);
                        socket.closeSocket();
                    }
                }

                @Override
                public void closeSocket() {
                    socket.closeSocket();
                }
            };
        }

        @Override
        public void onOpen() {
            pageSession = liveSessions.findOrCreate(sessionId).orElse(null);
            if (pageSession == null) {
                logger.log(WARNING, () -> "Local page session not found, reload remote on: " + handshakeRequest.url);
                sendControl(RspTransportProtocol.resumeRejected("session-not-found"));
            }
        }

        @Override
        public void onText(final String message) throws WebSocketProtocolException {
            logger.log(TRACE, () -> sessionId + " -> " + message);
            if (pageSession == null) {
                return;
            }

            final Optional<RspTransportProtocol.ClientControl> control =
                    RspTransportProtocol.decodeClientControl(message);
            if (attachmentHandle == null) {
                if (control.isEmpty() || !(control.get() instanceof RspTransportProtocol.Resume resume)) {
                    throw new WebSocketProtocolException(WebSocketFrame.CLOSE_PROTOCOL_ERROR,
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
                            throw new WebSocketProtocolException(WebSocketFrame.CLOSE_PROTOCOL_ERROR,
                                                                 "RSP connection is already resumed");
                }
            }
        }

        @Override
        public void onBinary(final byte[] payload) throws IOException {
            if (pageSession != null && attachmentHandle != null) {
                pageSession.terminate(attachmentHandle,
                                      "binary-protocol-message",
                                      WebSocketFrame.CLOSE_UNSUPPORTED_DATA);
            } else {
                socket.close(WebSocketFrame.CLOSE_UNSUPPORTED_DATA, "Binary RSP protocol is not supported yet");
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
                socket.close(WebSocketFrame.CLOSE_PROTOCOL_ERROR, reason);
            } catch (final IOException ex) {
                logger.log(DEBUG, "Failed to reject WebSocket resume for " + sessionId, ex);
                socket.closeSocket();
            }
        }

        private void sendControl(final String message) {
            try {
                sendText(message);
            } catch (final IOException ex) {
                logger.log(DEBUG, "Failed to send RSP transport control for " + sessionId, ex);
                socket.closeSocket();
            }
        }

        private void sendText(final String text) throws IOException {
            if (!socket.isOpen()) {
                throw new IOException("WebSocket is closed");
            }
            logger.log(TRACE, () -> sessionId + " <- " + text);
            socket.sendText(text);
        }

        private static boolean isFatalProtocolClose(final int code) {
            return code == WebSocketFrame.CLOSE_PROTOCOL_ERROR
                   || code == WebSocketFrame.CLOSE_UNSUPPORTED_DATA
                   || code == WebSocketFrame.CLOSE_INVALID_PAYLOAD
                   || code == WebSocketFrame.CLOSE_MESSAGE_TOO_BIG;
        }
    }
}
