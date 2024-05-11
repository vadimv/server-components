package rsp.server;

import rsp.dom.TreePositionPath;
import rsp.util.json.JsonDataType;

public interface RemoteIn {
    void handleExtractPropertyResponse(int descriptorId, ExtractPropertyResponse result);

    void handleDomEvent(int renderNumber, TreePositionPath path, String eventType, JsonDataType.Object eventObject);

    void handleEvalJsResponse(int descriptorId, JsonDataType value);
}
