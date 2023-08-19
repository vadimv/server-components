package rsp.util.json;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.Test;


import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonSimpleUtilsTests {

    @Test
    public void should_correctly_create_new_json_dt_from_simple_json_for_string() {
        final JsonDataType result = JsonSimpleUtils.convertToJsonType("bar");

        assertEquals(new JsonDataType.String("bar"), result);
    }

    @Test
    public void should_correctly_create_new_json_dt_from_simple_json_for_null() {
        final JsonDataType result = JsonSimpleUtils.convertToJsonType(null);

        assertEquals(JsonDataType.Null.INSTANCE, result);
    }

    @Test
    public void should_correctly_create_new_json_dt_fromm_simple_json_for_boolean() {
        assertEquals(new JsonDataType.Boolean(false), JsonSimpleUtils.convertToJsonType(false));
        assertEquals(new JsonDataType.Boolean(true), JsonSimpleUtils.convertToJsonType(true));
    }

    @Test
    public void should_correctly_create_new_json_dt_from_simple_json_for_number_integer() {
        final JsonDataType result = JsonSimpleUtils.convertToJsonType(101);

        assertEquals(new JsonDataType.Number(101), result);
    }

    @Test
    public void should_correctly_create_new_json_dt_from_simple_json_for_number_integer_long() {
        final JsonDataType result = JsonSimpleUtils.convertToJsonType(101);

        assertEquals(new JsonDataType.Number(101L), result);
    }

    @Test
    public void should_correctly_create_new_json_dt_from_simple_json_for_number_float() {
        final JsonDataType result = JsonSimpleUtils.convertToJsonType(1001.01F);

        assertEquals(new JsonDataType.Number(1001.01F), result);
    }

    @Test
    public void should_correctly_create_new_json_dt_from_simple_empty_json_object() throws ParseException {
        final JSONParser jsonParser = new JSONParser();
        final JSONObject json = (JSONObject) jsonParser.parse("{}");
        final JsonDataType result = JsonSimpleUtils.convertToJsonType(json);


        final JsonDataType.Object expected = new JsonDataType.Object();
        assertEquals(expected, result);
    }

    @Test
    public void should_correctly_create_new_json_dt_from_simple_json_object() throws ParseException {
        final JSONParser jsonParser = new JSONParser();
        final JSONObject json = (JSONObject) jsonParser.parse("{ \"key1\":\"value1\", \"key2\":\"value2\" }");
        final JsonDataType result = JsonSimpleUtils.convertToJsonType(json);

        final JsonDataType.Object expected = new JsonDataType.Object(Map.of("key1", new JsonDataType.String("value1"),
                                                                            "key2", new JsonDataType.String("value2")));
        assertEquals(expected, result);
    }

    @Test
    public void should_correctly_create_new_json_dt_from_json_array() throws ParseException {
        final JSONParser jsonParser = new JSONParser();
        final JSONArray json = (JSONArray) jsonParser.parse("[\"value1\", 64]");
        final JsonDataType result = JsonSimpleUtils.convertToJsonType(json);

        final JsonDataType.Array expected = new JsonDataType.Array(new JsonDataType.String("value1"),
                                                                   new JsonDataType.Number(64));
        assertEquals(expected, result);
    }

}
