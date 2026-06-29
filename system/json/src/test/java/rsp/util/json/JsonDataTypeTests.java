package rsp.util.json;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behaviour of the {@link JsonDataType} value tree: accessors, {@code toString} (compact JSON via
 * {@link JsonWriter}) and the {@code equals}/{@code hashCode} contract — including the
 * value-based numeric equality that {@link BigDecimal}-backed {@link JsonDataType.Number} provides.
 */
class JsonDataTypeTests {

    @Test
    void null_renders_as_null() {
        assertEquals("null", JsonDataType.Null.INSTANCE.toString());
    }

    @Test
    void string_renders_quoted() {
        final JsonDataType json = new JsonDataType.String("value-0");
        assertTrue(json instanceof JsonDataType.String(String s) && "value-0".equals(s));
        assertEquals("\"value-0\"", json.toString());
    }

    @Test
    void boolean_renders_as_keyword() {
        final JsonDataType json = new JsonDataType.Boolean(true);
        assertTrue(json instanceof JsonDataType.Boolean b && b.value());
        assertEquals("true", json.toString());
    }

    @Test
    void integral_number() {
        final JsonDataType.Number json = JsonDataType.Number.of(1001);
        assertEquals(1001, json.asLong());
        assertTrue(json.isIntegral());
        assertEquals("1001", json.toString());
    }

    @Test
    void fractional_number() {
        final JsonDataType.Number json = JsonDataType.Number.of(1001.01D);
        assertEquals(1001.01D, json.asDouble());
        assertTrue(json.isFractional());
        assertEquals("1001.01", json.toString());
    }

    @Test
    void numeric_equality_is_by_value_ignoring_scale() {
        assertEquals(new JsonDataType.Number(new BigDecimal("1.0")),
                new JsonDataType.Number(new BigDecimal("1.00")));
        assertEquals(new JsonDataType.Number(new BigDecimal("1.0")).hashCode(),
                new JsonDataType.Number(new BigDecimal("1.00")).hashCode());
        assertEquals(JsonDataType.Number.of(100), new JsonDataType.Number(new BigDecimal("1e2")));
    }

    @Test
    void empty_object() {
        final JsonDataType.Object json = new JsonDataType.Object();
        assertEquals(0, json.keys().size());
        assertEquals("{}", json.toString());
    }

    @Test
    void object_put_and_render() {
        final JsonDataType.Object json = new JsonDataType.Object().put("key0", new JsonDataType.Boolean(true));
        assertEquals(1, json.keys().size());
        assertEquals(new JsonDataType.Boolean(true), json.value("key0"));
        assertEquals("{\"key0\":true}", json.toString());
    }

    @Test
    void empty_array() {
        final JsonDataType.Array json = new JsonDataType.Array();
        assertEquals(0, json.size());
        assertTrue(json.isEmpty());
        assertEquals("[]", json.toString());
    }

    @Test
    void nested_array() {
        final JsonDataType.Array json = new JsonDataType.Array(new JsonDataType.Array(), new JsonDataType.Object());
        assertEquals(2, json.size());
        assertEquals(new JsonDataType.Array(), json.get(0));
        assertEquals(new JsonDataType.Object(), json.get(1));
        assertEquals("[[],{}]", json.toString());
    }

    @Test
    void complex_value_renders_compactly() {
        final JsonDataType.Array json = new JsonDataType.Array(
                new JsonDataType.Boolean(true),
                new JsonDataType.String("value-1"),
                new JsonDataType.Object().put("key", new JsonDataType.String("value")));
        assertEquals("[true,\"value-1\",{\"key\":\"value\"}]", json.toString());
    }

    @Test
    void object_equals_hashcode_contract() {
        final JsonDataType.Object o1 = new JsonDataType.Object();
        final JsonDataType.Object o2 = new JsonDataType.Object();
        assertEquals(o1, o2);
        assertEquals(o1.hashCode(), o2.hashCode());
        assertNotEquals(o1, new JsonDataType.Object(Map.of("num0", JsonDataType.Number.of(1.1))));
    }

    @Test
    void array_equals_hashcode_contract() {
        final JsonDataType.Array a1 = new JsonDataType.Array(JsonDataType.Number.of(1), new JsonDataType.String("str0"), new JsonDataType.Array());
        final JsonDataType.Array a2 = new JsonDataType.Array(JsonDataType.Number.of(1), new JsonDataType.String("str0"), new JsonDataType.Array());
        assertEquals(a1, a2);
        assertEquals(a1.hashCode(), a2.hashCode());
        assertNotEquals(a1, new JsonDataType.Array(JsonDataType.Number.of(1), new JsonDataType.String("str1"), new JsonDataType.Array()));
    }
}
