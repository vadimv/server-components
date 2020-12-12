package rsp.server;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.dom.RemoteDomChangesPerformer;

import java.util.List;

public interface OutMessages {
    void setRenderNum(int renderNum);
    void listenEvent(String eventType, boolean preventDefault, VirtualDomPath path, Event.Modifier modifier);
    void extractProperty(int descriptor, VirtualDomPath path, String name);
    void modifyDom(List<RemoteDomChangesPerformer.DomChange> domChange);
    void setHref(String path);
    void pushHistory(String path);
    void evalJs(int descriptor, String js);
}
