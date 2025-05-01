package rsp.util.json;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;


public class JsonDataTypeTests {

    @Test
    public void produces_valid_null_json() {
        final JsonDataType json = JsonDataType.Null.INSTANCE;
        Assertions.assertEquals("null", json.toString());
    }

    @Test
    public void produces_valid_string_json() {
        final JsonDataType json = new JsonDataType.String("value-0");
        Assertions.assertEquals("value-0", json.asJsonString().value());
        Assertions.assertEquals("\"value-0\"", json.toString());
    }

    @Test
    public void produces_valid_boolean_json() {
        final JsonDataType json = new JsonDataType.Boolean(true);
        Assertions.assertEquals(true, json.asJsonBoolean().value());
        Assertions.assertEquals("true", json.toString());
    }

    @Test
    public void produces_valid_int_json() {
        final JsonDataType json = new JsonDataType.Number(1001);
        Assertions.assertEquals(1001, json.asJsonNumber().asLong());
        Assertions.assertEquals("1001", json.toString());
    }

    @Test
    public void produces_valid_double_json() {
        final JsonDataType json = new JsonDataType.Number(1001.01D);
        Assertions.assertEquals(1001.01D, json.asJsonNumber().value());
        Assertions.assertEquals("1001.01", json.toString());
    }

    @Test
    public void produces_valid_empty_object_json() {
        final JsonDataType json = new JsonDataType.Object();
        Assertions.assertEquals(0, json.asJsonObject().keys().size());
        Assertions.assertEquals("{}", json.toString());
    }

    @Test
    public void produces_valid_object_json() {
        final JsonDataType.Object json = new JsonDataType.Object().put("key0", new JsonDataType.Boolean(true));
        Assertions.assertEquals(1, json.asJsonObject().keys().size());
        Assertions.assertEquals(true, json.value("key0").orElseThrow().asJsonBoolean().value());
        Assertions.assertEquals("{\"key0\": true}", json.toString());
    }

    @Test
    public void produces_valid_empty_array_json() {
        final JsonDataType json = new JsonDataType.Array();
        Assertions.assertEquals(0, json.asJsonArray().size());
        Assertions.assertEquals("[]", json.toString());
    }

    @Test
    public void produces_valid_array_json() {
        final JsonDataType json = new JsonDataType.Array(new JsonDataType.Array(), new JsonDataType.Object());
        Assertions.assertEquals(2, json.asJsonArray().size());
        Assertions.assertEquals(json.asJsonArray().get(0), new JsonDataType.Array());
        Assertions.assertEquals(json.asJsonArray().get(1), new JsonDataType.Object());
        Assertions.assertEquals("[[],{}]", json.toString());
    }

    @Test
    public void produces_valid_complex_json() {
        final JsonDataType.Array json = new JsonDataType.Array(
                new JsonDataType.Boolean(true),
                new JsonDataType.String("value-1"),
                new JsonDataType.Object().put("key", new JsonDataType.String("value"))
        );
        Assertions.assertEquals("[true,\"value-1\",{\"key\": \"value\"}]", json.toString());
    }

    @Test
    public void should_comply_to_equals_hash_contract_object() {
        final JsonDataType.Object o1 = new JsonDataType.Object();
        final JsonDataType.Object o2 = new JsonDataType.Object();
        Assertions.assertEquals(o1, o2);
        Assertions.assertEquals(o1.hashCode(), o2.hashCode());
        final JsonDataType.Object o3= new JsonDataType.Object(Map.of("num0",JsonDataType.Number.of(1.1)));
        Assertions.assertNotEquals(o1, o3);
    }

    @Test
    public void should_comply_to_equals_hash_contract_array() {
        final JsonDataType.Array a1 = new JsonDataType.Array(JsonDataType.Number.of(1), new JsonDataType.String("str0"), new JsonDataType.Array());
        final JsonDataType.Array a2 = new JsonDataType.Array(JsonDataType.Number.of(1), new JsonDataType.String("str0"), new JsonDataType.Array());
        Assertions.assertEquals(a1, a2);
        Assertions.assertEquals(a1.hashCode(), a2.hashCode());
        final JsonDataType.Array a3 = new JsonDataType.Array(JsonDataType.Number.of(1), new JsonDataType.String("str1"), new JsonDataType.Array());
        Assertions.assertNotEquals(a1, a3);
    }


}
