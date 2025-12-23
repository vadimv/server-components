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

    /**
     * Listens for events on the client-side.
     * @param events a list of events to listen for, must not be null
     */
    void listenEvents(List<DomEventEntry> events);

    /**
     * Forgets an event on the client-side.
     * @param eventType the type of the event, must not be null
     * @param elementPath the path to the element, must not be null
     */
    void forgetEvent(String eventType, TreePositionPath elementPath);

    /**
     * Extracts a property from an element on the client-side.
     * @param descriptor the descriptor of the request
     * @param path the path to the element, must not be null
     * @param name the name of the property, must not be null
     */
    void extractProperty(int descriptor, TreePositionPath path, String name);

    /**
     * Modifies the DOM on the client-side.
     * @param domChange a list of DOM changes, must not be null
     */
    void modifyDom(List<DefaultDomChangesContext.DomChange> domChange);

    /**
     * Sets the href of the window on the client-side.
     * @param path the new href, must not be null
     */
    void setHref(String path);

    /**
     * Pushes a new entry to the browser's history.
     * @param path the new path, must not be null
     */
    void pushHistory(String path);

    /**
     * Evaluates JavaScript on the client-side.
     * @param descriptor the descriptor of the request
     * @param js the JavaScript to evaluate, must not be null
     */
    void evalJs(int descriptor, String js);
}
