package rsp.server;

import rsp.dom.VirtualDomPath;
import rsp.util.json.JsonDataType;

public interface RemoteIn {
    void handleExtractPropertyResponse(int descriptorId, ExtractPropertyResponse result);

    void handleDomEvent(int renderNumber, VirtualDomPath path, String eventType, JsonDataType.Object eventObject);

    void handleEvalJsResponse(int descriptorId, JsonDataType value);
}
