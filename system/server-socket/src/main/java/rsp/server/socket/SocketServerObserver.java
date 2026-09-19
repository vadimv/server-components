package rsp.server.socket;

/** Optional lifecycle/traffic observer for the socket transport. */
public interface SocketServerObserver {
    SocketServerObserver NOOP = new SocketServerObserver() {
    };

    default void requestReceived() {
    }

    default void requestFailed() {
    }

    default void activeWebSocketsChanged(int activeConnections) {
    }

    default WebSocketObserver openWebSocket() {
        return WebSocketObserver.NOOP;
    }

    /** Per-connection message and payload-byte activity. */
    interface WebSocketObserver extends AutoCloseable {
        WebSocketObserver NOOP = new WebSocketObserver() {
        };

        default void messageReceived(int payloadBytes) {
        }

        default void messageSent(int payloadBytes) {
        }

        @Override
        default void close() {
        }
    }
}
