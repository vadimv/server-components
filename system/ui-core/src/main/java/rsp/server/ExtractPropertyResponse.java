package rsp.server;

import rsp.util.json.JsonDataType;

import java.util.Objects;

public sealed interface ExtractPropertyResponse {

    ExtractPropertyResponse NOT_FOUND = new NotFound();

    record NotFound() implements ExtractPropertyResponse {}

    record Value(JsonDataType value) implements ExtractPropertyResponse {
        public Value {
            Objects.requireNonNull(value);
        }
    }
}
