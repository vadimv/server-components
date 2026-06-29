package rsp.util.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Resource-exhaustion defenses. Every adversarial input must raise {@link JsonDataType.JsonException}
 * — never {@link OutOfMemoryError}, {@link StackOverflowError}, or a hang.
 */
@Timeout(10)
class JsonSecurityTests {

    // --- number expansion (small input, catastrophic to expand) -------------------------

    @Test
    void rejects_huge_exponent_without_expanding() {
        // 12 chars; cheap to construct as BigDecimal but a ~1GB expansion. Must be rejected.
        assertThrows(JsonDataType.JsonException.class, () -> Json.parse("1e1000000000"));
    }

    @Test
    void rejects_exponent_with_more_digits_than_an_int() {
        assertThrows(JsonDataType.JsonException.class, () -> Json.parse("1e99999999999999999999"));
    }

    @Test
    void enforces_exponent_bound_at_the_boundary() {
        assertEquals(new JsonDataType.Number(new BigDecimal("1e10000")), Json.parse("1e10000")); // at limit: allowed
        assertThrows(JsonDataType.JsonException.class, () -> Json.parse("1e10001")); // over limit
    }

    @Test
    void rejects_number_with_too_many_digits() {
        final String thousandOne = "9".repeat(1001);
        assertThrows(JsonDataType.JsonException.class, () -> Json.parse(thousandOne));
    }

    // --- nesting depth (small input, blows the stack) ------------------------------------------

    @Test
    void rejects_deeply_nested_arrays_without_stack_overflow() {
        final String deep = "[".repeat(100_000) + "]".repeat(100_000);
        assertThrows(JsonDataType.JsonException.class, () -> Json.parse(deep));
    }

    @Test
    void rejects_deeply_nested_objects_without_stack_overflow() {
        final String deep = "{\"a\":".repeat(100_000) + "1" + "}".repeat(100_000);
        assertThrows(JsonDataType.JsonException.class, () -> Json.parse(deep));
    }

    // --- defense-in-depth count / size caps (enforced via tight custom limits) -----------------

    @Test
    void enforces_max_array_elements() {
        final JsonLimits limits = new JsonLimits(512, 1000, 10000, 1_000_000, 3, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertThrows(JsonDataType.JsonException.class, () -> Json.parse("[1,2,3,4]", limits));
        assertEquals(3, ((JsonDataType.Array) Json.parse("[1,2,3]", limits)).size());
    }

    @Test
    void enforces_max_object_entries() {
        final JsonLimits limits = new JsonLimits(512, 1000, 10000, 2, 1_000_000, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertThrows(JsonDataType.JsonException.class, () -> Json.parse("{\"a\":1,\"b\":2,\"c\":3}", limits));
    }

    @Test
    void enforces_max_string_length() {
        final JsonLimits limits = new JsonLimits(512, 1000, 10000, 1_000_000, 1_000_000, 4, Integer.MAX_VALUE);
        assertThrows(JsonDataType.JsonException.class, () -> Json.parse("\"hello\"", limits));
        assertEquals(new JsonDataType.String("hi"), Json.parse("\"hi\"", limits));
    }

    @Test
    void enforces_max_input_length() {
        final JsonLimits limits = new JsonLimits(512, 1000, 10000, 1_000_000, 1_000_000, Integer.MAX_VALUE, 3);
        assertThrows(JsonDataType.JsonException.class, () -> Json.parse("[1,2,3,4]", limits));
    }

    @Test
    void enforces_max_depth() {
        final JsonLimits limits = new JsonLimits(3, 1000, 10000, 1_000_000, 1_000_000, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertThrows(JsonDataType.JsonException.class, () -> Json.parse("[[[[1]]]]", limits));
        assertEquals(Json.parse("[[[1]]]", limits), Json.parse("[[[1]]]", limits));
    }
}
