package rsp.util.json;

import org.json.simple.parser.ParseException;

import java.util.*;

/**
 * json-simple related.
 */
public class JsonSimpleUtils {

    private JsonSimpleUtils() {}

    public static JsonParser createParser() throws JsonDataType.JsonException {
        return jsonString -> parse(jsonString);
    }

    public static JsonDataType parse(final String s) throws JsonDataType.JsonException {
        try {
            return convertToJsonType(new org.json.simple.parser.JSONParser().parse(s));
        } catch (final ParseException ex) {
            throw new JsonDataType.JsonException("Parse exception", ex);
        }
    }

    /**
     * Recursively converts a json-simple parsed JSON object to a {@link JsonDataType}.
     * @param j an input object
     * @return the conversion result
     */
    public static JsonDataType convertToJsonType(final Object j) {
        if (j == null) {
            return JsonDataType.Null.INSTANCE;
        } else if (j instanceof org.json.simple.JSONObject) {
            final org.json.simple.JSONObject jsonObject = (org.json.simple.JSONObject) j;
            @SuppressWarnings("unchecked")
            final Set<Map.Entry<?, ?>> entrySet = jsonObject.entrySet();
            final Map<String, JsonDataType> m = new HashMap<>();
            for (final Map.Entry<?, ?> entry : entrySet) {
                m.put((String) entry.getKey(), convertToJsonType(entry.getValue()));
            }
            return new JsonDataType.Object(m);
        } else if (j instanceof org.json.simple.JSONArray){
            final org.json.simple.JSONArray jsonArray = (org.json.simple.JSONArray) j;
            final List<JsonDataType> a = new ArrayList<>();
            for(final Object item : jsonArray) {
                a.add(convertToJsonType(item));
            }
            return new JsonDataType.Array(a.toArray(new JsonDataType[0]));
        } else if (j instanceof String) {
            return new JsonDataType.String((String) j);
        } else if (j instanceof Boolean) {
            return new JsonDataType.Boolean((Boolean) j);
        } else if (j instanceof Long) {
            return new JsonDataType.Number(((Long) j));
        } else if (j instanceof Integer) {
            return new JsonDataType.Number(((Integer) j));
        } else if (j instanceof Double) {
            return new JsonDataType.Number(((Double) j));
        } else if (j instanceof Float) {
            return new JsonDataType.Number(((Float) j));
        } else {
            throw new IllegalStateException("Unsupported json-simple data type: " + j.getClass().getName());
        }
    }
}
