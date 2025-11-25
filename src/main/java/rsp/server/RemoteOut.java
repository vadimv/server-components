package rsp.server;

import rsp.dom.EventEntry;
import rsp.dom.TreePositionPath;
import rsp.dom.DefaultDomChangesContext;

import java.util.List;

public interface RemoteOut {
    void setRenderNum(int renderNum);
    void listenEvents(List<EventEntry> events);
    void forgetEvent(String eventType, TreePositionPath elementPath);
    void extractProperty(int descriptor, TreePositionPath path, String name);
    void modifyDom(List<DefaultDomChangesContext.DomChange> domChange);
    void setHref(String path);
    void pushHistory(String path);
    void evalJs(int descriptor, String js);
}
