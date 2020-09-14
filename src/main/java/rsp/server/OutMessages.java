package rsp.server;

import rsp.dom.Path;
import rsp.dom.RemoteDomChangesPerformer;

public interface OutMessages {
    void setRenderNum(int renderNum);
    void listenEvent(String eventType, boolean b);
    void extractProperty(Path path, String name, int descriptor);
    void modifyDom(RemoteDomChangesPerformer.DomChange domChange);
    void evalJs(int descriptor, String js);
}
