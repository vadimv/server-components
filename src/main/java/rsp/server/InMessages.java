package rsp.server;

import rsp.dom.VirtualDomPath;
import rsp.util.json.JsonDataType;

public interface InMessages {
    void extractPropertyResponse(int descriptorId, JsonDataType value);

    void domEvent(int renderNumber, VirtualDomPath path, String eventType, JsonDataType.Object eventObject);

    void evalJsResponse(int descriptorId, JsonDataType value);
}
