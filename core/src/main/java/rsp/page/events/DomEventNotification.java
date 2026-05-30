package rsp.page.events;

import rsp.dom.NodeId;
import rsp.util.json.JsonDataType;

public record DomEventNotification(int renderNumber, NodeId nodeId, String eventType, JsonDataType.Object eventObject) implements Command {
}
