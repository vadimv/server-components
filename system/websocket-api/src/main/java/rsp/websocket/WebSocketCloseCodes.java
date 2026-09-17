package rsp.websocket;

/** Named RFC 6455 close status codes used by endpoints and transports. */
public final class WebSocketCloseCodes {
    public static final int NORMAL = 1000;
    public static final int GOING_AWAY = 1001;
    public static final int PROTOCOL_ERROR = 1002;
    public static final int UNSUPPORTED_DATA = 1003;
    public static final int INVALID_PAYLOAD = 1007;
    public static final int POLICY_VIOLATION = 1008;
    public static final int MESSAGE_TOO_BIG = 1009;
    public static final int INTERNAL_ERROR = 1011;

    private WebSocketCloseCodes() {
    }
}
