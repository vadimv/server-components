package rsp.page;

import rsp.dom.VirtualDomPath;
import rsp.util.json.JsonDataType;

public interface EventDispatcher {
    void dispatchEvent(VirtualDomPath eventElementPath, String eventName, JsonDataType.Object eventData);
}
