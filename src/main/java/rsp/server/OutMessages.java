package rsp.server;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.dom.DefaultDomChangesPerformer;

import java.util.List;

public interface OutMessages {
    void setRenderNum(int renderNum);
    void listenEvents(List<Event> events);
    void forgetEvent(String eventType, VirtualDomPath elementPath);
    void extractProperty(int descriptor, VirtualDomPath path, String name);
    void modifyDom(List<DefaultDomChangesPerformer.DomChange> domChange);
    void setHref(String path);
    void pushHistory(String path);
    void evalJs(int descriptor, String js);
}
