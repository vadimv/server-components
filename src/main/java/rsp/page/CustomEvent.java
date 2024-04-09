package rsp.page;

import rsp.util.json.JsonDataType;

public record CustomEvent(String eventName, JsonDataType.Object eventData) {
}
