package rsp.util.json;

import java.util.*;

// TODO unit test
/**
 * json-simple related.
 */
public class JsonSimple {
    /**
     * Recursively converts a json-simple parsed JSON object to a {@link JsonDataType}.
     * @param j an input object
     * @return the conversion result
     */
    public static JsonDataType convertToJsonType(Object j) {
        if (j == null) {
            return JsonDataType.Null.INSTANCE;
        } else if (j instanceof org.json.simple.JSONObject) {
            final org.json.simple.JSONObject jsonObject = (org.json.simple.JSONObject) j;
            final Set<Map.Entry> entrySet = jsonObject.entrySet();
            final Map<String, JsonDataType> m = new HashMap<>();
            for (Map.Entry entry : entrySet) {
                m.put((String) entry.getKey(), convertToJsonType(entry.getValue()));
            }
            return new JsonDataType.Object(Collections.unmodifiableMap(m));
        } else if (j instanceof org.json.simple.JSONArray){
            final org.json.simple.JSONArray jsonArray = (org.json.simple.JSONArray) j;
            final List<JsonDataType> a = new ArrayList<>();
            for(Object item : jsonArray) {
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
