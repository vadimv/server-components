package rsp.server;

import rsp.dom.Path;

public interface InMessages {
    void extractProperty(int descriptorId, String value);

    void domEvent(int renderNumber, Path path, String eventType, String eventObject);

    void evalJsResponse(int descriptorId, String value);
}
