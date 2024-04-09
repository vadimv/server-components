package rsp.page;

import rsp.dom.VirtualDomPath;

public interface EventDispatcher {
    void dispatchEvent(VirtualDomPath eventElementPath, CustomEvent customEvent);
}
