package rsp.server.jdk;

/** Optional lifecycle/traffic observer for the JDK transport. */
public interface JdkServerObserver {
    JdkServerObserver NOOP = new JdkServerObserver() {
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
