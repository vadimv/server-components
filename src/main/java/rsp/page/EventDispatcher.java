package rsp.page;

import rsp.dom.TreePositionPath;

public interface EventDispatcher {
    void dispatchEvent(TreePositionPath eventElementPath, CustomEvent customEvent);
}
