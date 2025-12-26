package rsp.page.events;

import rsp.util.json.JsonDataType;

public record EvalJsResponseEvent(int descriptorId, JsonDataType value) implements Command {
}
