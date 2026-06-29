package rsp.util.json;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonWriterTests {

    @Test
    void writes_scalars() {
        assertEquals("null", Json.write(JsonDataType.Null.INSTANCE));
        assertEquals("true", Json.write(new JsonDataType.Boolean(true)));
        assertEquals("\"hi\"", Json.write(new JsonDataType.String("hi")));
        assertEquals("42", Json.write(JsonDataType.Number.of(42)));
    }

    @Test
    void escapes_string_values_and_object_keys() {
        assertEquals("\"a\\\"b\\\\c\\n\"", Json.write(new JsonDataType.String("a\"b\\c\n")));
        assertEquals("\"\\u262f\"", Json.write(new JsonDataType.String("☯")));

        final Map<String, JsonDataType> m = new LinkedHashMap<>();
        m.put("a\"b", new JsonDataType.Boolean(true));
        assertEquals("{\"a\\\"b\":true}", Json.write(new JsonDataType.Object(m)));
    }

    @Test
    void writes_compact_containers_in_insertion_order() {
        final Map<String, JsonDataType> m = new LinkedHashMap<>();
        m.put("b", JsonDataType.Number.of(1));
        m.put("a", JsonDataType.Number.of(2));
        assertEquals("{\"b\":1,\"a\":2}", Json.write(new JsonDataType.Object(m)));
        assertEquals("[1,\"x\",null]",
                Json.write(new JsonDataType.Array(JsonDataType.Number.of(1),
                        new JsonDataType.String("x"), JsonDataType.Null.INSTANCE)));
    }

    @Test
    void writes_huge_exponent_compactly_not_expanded() {
        // The crux of the DoS-safe writer: a value with a billion-place scale must stay tiny.
        final JsonDataType.Number n = new JsonDataType.Number(new BigDecimal("1e1000000000"));
        final String out = Json.write(n);
        assertTrue(out.length() < 32, "expected compact scientific form, got length " + out.length());
        // Round-trips only under a permissive limit — default limits reject this exponent by design.
        final JsonLimits permissive = new JsonLimits(512, 1000, Integer.MAX_VALUE,
                1_000_000, 1_000_000, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(n, Json.parse(out, permissive));
    }

    @Test
    void toString_delegates_to_writer() {
        assertEquals("{\"a\":[1,2]}", new JsonDataType.Object(Map.of("a",
                new JsonDataType.Array(JsonDataType.Number.of(1), JsonDataType.Number.of(2)))).toString());
    }
}
