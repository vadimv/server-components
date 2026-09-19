package rsp.util.json;

import java.util.Objects;

/** Bidirectional mapping between a domain value and the immutable JSON value tree. */
public interface JsonCodec<T> {
    /** Decodes one JSON value, throwing {@link JsonDecodingException} for an invalid shape. */
    T decode(JsonDataType value);

    /** Encodes one domain value as JSON. */
    JsonDataType encode(T value);

    /** Creates a codec from separate decoding and encoding functions. */
    static <T> JsonCodec<T> of(Decoder<T> decoder, Encoder<T> encoder) {
        Objects.requireNonNull(decoder, "decoder");
        Objects.requireNonNull(encoder, "encoder");
        return new JsonCodec<>() {
            @Override
            public T decode(JsonDataType value) {
                return Objects.requireNonNull(decoder.decode(Objects.requireNonNull(value, "value")),
                        "decoded value");
            }

            @Override
            public JsonDataType encode(T value) {
                return Objects.requireNonNull(encoder.encode(Objects.requireNonNull(value, "value")),
                        "encoded JSON value");
            }
        };
    }

    /** Identity codec for handlers that work directly with {@link JsonDataType}. */
    static JsonCodec<JsonDataType> tree() {
        return of(value -> value, value -> value);
    }

    @FunctionalInterface
    interface Decoder<T> {
        T decode(JsonDataType value);
    }

    @FunctionalInterface
    interface Encoder<T> {
        JsonDataType encode(T value);
    }
}
