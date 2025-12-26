package rsp.page;

import rsp.dom.TreePositionPath;
import rsp.page.events.CustomEvent;

public interface EventDispatcher {
    /**
     * Dispatches a custom event.
     * @param eventElementPath the path to the element, must not be null
     * @param customEvent the custom event, must not be null
     */
    void dispatchEvent(TreePositionPath eventElementPath, CustomEvent customEvent);
}
