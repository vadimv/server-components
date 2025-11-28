package rsp.page.events;

import rsp.util.json.JsonDataType;

public record ComponentEventNotification(String eventType, JsonDataType.Object eventObject) implements SessionEvent {
}
