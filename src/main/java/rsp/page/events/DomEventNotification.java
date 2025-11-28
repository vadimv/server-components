package rsp.page.events;

import rsp.dom.TreePositionPath;
import rsp.util.json.JsonDataType;

public record DomEventNotification(int renderNumber, TreePositionPath path, String eventType, JsonDataType.Object eventObject) implements SessionEvent {
}
