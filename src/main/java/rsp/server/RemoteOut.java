package rsp.server;

import rsp.dom.DomEventEntry;
import rsp.dom.TreePositionPath;
import rsp.dom.DefaultDomChangesContext;

import java.util.List;

/**
 * Represents remote actions within an open live session.
 * It is expected that calling the methods of this interface lead to sending a message over
 * the open channel and executing of a relevant action on client-side.
 * @link ./src/main/es6/rsp.js
 * */
public interface RemoteOut {

    void setRenderNum(int renderNum);

    void listenEvents(List<DomEventEntry> events);

    void forgetEvent(String eventType, TreePositionPath elementPath);

    void extractProperty(int descriptor, TreePositionPath path, String name);

    void modifyDom(List<DefaultDomChangesContext.DomChange> domChange);

    void setHref(String path);

    void pushHistory(String path);

    void evalJs(int descriptor, String js);
}
