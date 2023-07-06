package rsp.server;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.dom.DefaultDomChangesContext;

import java.util.List;

public interface RemoteOut {
    void setRenderNum(int renderNum);
    void listenEvents(List<Event> events);
    void forgetEvent(String eventType, VirtualDomPath elementPath);
    void extractProperty(int descriptor, VirtualDomPath path, String name);
    void modifyDom(List<DefaultDomChangesContext.DomChange> domChange);
    void setHref(String path);
    void pushHistory(String path);
    void evalJs(int descriptor, String js);
}
