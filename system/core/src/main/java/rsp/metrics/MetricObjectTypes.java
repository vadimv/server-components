package rsp.metrics;

/**
 * Fixed schemas for lifecycle-bound metric objects owned by the framework.
 * Metric names and JMX attributes are public stability contracts.
 */
public final class MetricObjectTypes {
    /** Complete WebSocket messages received by one connection. */
    public static final String WEB_SOCKET_MESSAGES_RECEIVED =
            "rsp.websocket.connection.messages.received";

    /** Complete WebSocket text or binary messages sent by one connection. */
    public static final String WEB_SOCKET_MESSAGES_SENT =
            "rsp.websocket.connection.messages.sent";

    /** Payload bytes in complete WebSocket messages received by one connection. */
    public static final String WEB_SOCKET_BYTES_RECEIVED =
            "rsp.websocket.connection.bytes.received";

    /** Payload bytes in complete WebSocket text or binary messages sent by one connection. */
    public static final String WEB_SOCKET_BYTES_SENT =
            "rsp.websocket.connection.bytes.sent";

    /** Fixed schema for one live WebSocket transport connection. */
    public static final MetricObjectType WEB_SOCKET_CONNECTION = new MetricObjectType(
            "rsp.websocket.connection",
            "WebSocketConnection",
            MetricCatalog.of(
                    MetricDescriptor.counter(WEB_SOCKET_MESSAGES_RECEIVED,
                                             "1",
                                             "Complete WebSocket messages received",
                                             "MessagesReceived"),
                    MetricDescriptor.counter(WEB_SOCKET_MESSAGES_SENT,
                                             "1",
                                             "Complete WebSocket text or binary messages sent",
                                             "MessagesSent"),
                    MetricDescriptor.counter(WEB_SOCKET_BYTES_RECEIVED,
                                             "By",
                                             "Payload bytes in complete WebSocket messages received",
                                             "BytesReceived"),
                    MetricDescriptor.counter(WEB_SOCKET_BYTES_SENT,
                                             "By",
                                             "Payload bytes in complete WebSocket text or binary messages sent",
                                             "BytesSent")));

    private static final MetricObjectCatalog FRAMEWORK_CATALOG =
            MetricObjectCatalog.of(WEB_SOCKET_CONNECTION);

    private MetricObjectTypes() {
    }

    /** Returns the fixed allow-list of framework-owned metric-object types. */
    public static MetricObjectCatalog frameworkCatalog() {
        return FRAMEWORK_CATALOG;
    }
}
