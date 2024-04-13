package rsp.server;

import rsp.util.json.JsonDataType;

public sealed interface ExtractPropertyResponse {

    ExtractPropertyResponse NOT_FOUND = new NotFound();

    record NotFound() implements ExtractPropertyResponse {}

    record Value(JsonDataType value) implements ExtractPropertyResponse {}
}
