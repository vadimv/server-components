package rsp.util.json;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonCodecTests {
    private static final JsonCodec<Message> MESSAGE_CODEC = JsonCodec.of(
            value -> new Message(Json.requireObject(value).requiredString("message")),
            message -> Json.object().put("message", message.value()));

    @Test
    void maps_domain_values_and_json_trees_in_both_directions() {
        Message message = new Message("hello");

        JsonDataType encoded = MESSAGE_CODEC.encode(message);

        assertEquals("{\"message\":\"hello\"}", encoded.toString());
        assertEquals(message, MESSAGE_CODEC.decode(encoded));
        assertEquals(encoded, JsonCodec.tree().decode(encoded));
    }

    @Test
    void object_conveniences_preserve_types_and_report_shape_failures() {
        JsonDataType.Object object = Json.object()
                .put("name", "Ada")
                .put("active", true)
                .put("count", 3L)
                .put("ratio", 1.5)
                .putNull("missing")
                .put("nested", Json.object().put("key", "value"))
                .put("items", Json.array(Json.string("one")));

        assertEquals("Ada", object.requiredString("name"));
        assertEquals(true, object.requiredBoolean("active"));
        assertEquals(3L, object.requiredNumber("count").asLong());
        assertEquals("value", object.requiredObject("nested").requiredString("key"));
        assertEquals(1, object.requiredArray("items").size());
        assertEquals(Json.nullValue(), object.required("missing"));
        assertEquals(4L, Json.requireNumber(Json.number(4L)).asLong());
        assertEquals(new BigDecimal("1.25"), Json.number(new BigDecimal("1.25")).value());
        assertEquals("value", Json.requireString(Json.string("value")));
        assertEquals(true, Json.requireBoolean(Json.bool(true)));
        assertEquals(1, Json.requireArray(Json.array(Json.string("one"))).size());
        assertThrows(JsonDecodingException.class, () -> object.requiredString("unknown"));
        assertThrows(JsonDecodingException.class, () -> object.requiredString("count"));
        assertThrows(JsonDecodingException.class, () -> Json.requireObject(Json.string("not an object")));
        assertThrows(JsonDecodingException.class, () -> Json.requireNumber(Json.bool(true)));
    }

    private record Message(String value) {
    }
}
