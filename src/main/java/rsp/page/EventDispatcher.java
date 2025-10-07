package rsp.page;

import rsp.dom.TreePositionPath;
import rsp.page.events.CustomEvent;

public interface EventDispatcher {
    void dispatchEvent(TreePositionPath eventElementPath, CustomEvent customEvent);
}
