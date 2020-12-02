package rsp.server;

import rsp.dom.Path;

import java.util.Optional;
import java.util.function.Function;

public interface InMessages {
    void extractPropertyResponse(int descriptorId, Object value);

    void domEvent(int renderNumber, Path path, String eventType, Function<String, Optional<String>> eventObject);

    void evalJsResponse(int descriptorId, String value);
}
