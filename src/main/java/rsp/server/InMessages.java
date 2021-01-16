package rsp.server;

import rsp.dom.VirtualDomPath;
import rsp.util.json.JsonDataType;

public interface InMessages {
    void handleExtractPropertyResponse(int descriptorId, JsonDataType value);

    void handleDomEvent(int renderNumber, VirtualDomPath path, String eventType, JsonDataType.Object eventObject);

    void handleEvalJsResponse(int descriptorId, JsonDataType value);
}
