package rsp.server;

import rsp.dom.VirtualDomPath;
import rsp.util.data.Either;
import rsp.util.json.JsonDataType;

public interface In {
    void handleExtractPropertyResponse(int descriptorId, Either<Throwable, JsonDataType> result);

    void handleDomEvent(int renderNumber, VirtualDomPath path, String eventType, JsonDataType.Object eventObject);

    void handleEvalJsResponse(int descriptorId, JsonDataType value);
}
