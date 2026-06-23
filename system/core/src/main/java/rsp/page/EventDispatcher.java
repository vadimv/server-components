package rsp.page;

import rsp.dom.NodeId;
import rsp.page.events.CustomEvent;

public interface EventDispatcher {
    /**
     * Dispatches a custom event.
     * @param nodeId the id of the element, must not be null
     * @param customEvent the custom event, must not be null
     */
    void dispatchEvent(NodeId nodeId, CustomEvent customEvent);
}
