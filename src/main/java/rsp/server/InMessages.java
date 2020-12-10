package rsp.server;

import rsp.dom.VirtualDomPath;

import java.util.Optional;
import java.util.function.Function;

public interface InMessages {
    void extractPropertyResponse(int descriptorId, Object value);

    void domEvent(int renderNumber, VirtualDomPath path, String eventType, Function<String, Optional<String>> eventObject);

    void evalJsResponse(int descriptorId, Object value);
}
