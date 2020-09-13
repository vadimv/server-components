package rsp.server;

public interface CallbackType {
    public static final int DomEvent = 0; // `$renderNum:$elementId:$eventType`
    public static final int CustomCallback = 1; // `$name:arg`
    public static final int ExtractPropertyResponse = 2; // `$descriptor:$value`
    public static final int History = 3; // URL
    public static final int EvalJsResponse = 4; // `$descriptor:$status:$value`
    public static final int ExtractEventDataResponse = 5; // `$descriptor:$dataJson`
    public static final int Heartbeat = 6; // `$descriptor:$anyvalue`

}
