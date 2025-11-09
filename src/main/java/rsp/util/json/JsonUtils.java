package rsp.util.json;

import org.json.simple.parser.ParseException;

import java.util.*;

public final class JsonUtils {

    private JsonUtils() {}

    public static JsonParser createParser() throws JsonDataType.JsonException {
        return JsonUtils::parse;
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
    static JsonDataType convertToJsonType(final Object j) {
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
        } else if (j instanceof String s) {
            return new JsonDataType.String(s);
        } else if (j instanceof Boolean b) {
            return new JsonDataType.Boolean(b);
        } else if (j instanceof Long l) {
            return new JsonDataType.Number(l);
        } else if (j instanceof Integer i) {
            return new JsonDataType.Number(i);
        } else if (j instanceof Double d) {
            return new JsonDataType.Number(d);
        } else if (j instanceof Float f) {
            return new JsonDataType.Number(f);
        } else {
            throw new IllegalStateException("Unsupported JSON data type: " + j.getClass().getName());
        }
    }

    public static String unescape(final String s) {
        final StringBuilder sb = new StringBuilder();
        int i = 0;
        final int len = s.length() - 1;
        while (i < len) {
            final char c = s.charAt(i);
            int charsConsumed = 0;
            if (c != '\\') {
                charsConsumed = 1;
                sb.append(c);
            } else {
                charsConsumed = 2;
                switch(s.charAt(i + 1)) {
                    case '\\': sb.append('\\'); break;
                    case '"': sb.append('"'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        final String code = s.substring(i + 2, i + 6);
                        charsConsumed = 6;
                        sb.append((char) Integer.parseInt(code, 16));
                        break;
                }
            }
            i += charsConsumed;
        }
        return sb.toString();
    }

    public static String escape(final String s) {
        final StringBuilder sb = new StringBuilder();
        int i = 0;
        final int len = s.length();
        while (i < len) {
            final char c = s.charAt(i);
            switch(c) {
                case '"'  : sb.append("\\\""); break;
                case '\\' : sb.append("\\\\"); break;
                case '\b' : sb.append("\\b"); break;
                case '\f' : sb.append("\\f"); break;
                case '\n' : sb.append("\\n"); break;
                case '\r' : sb.append("\\r"); break;
                case '\t' : sb.append("\\t"); break;
                default:
                    if (c < ' ' || (c > '~')) sb.append(String.format("\\u%04x", (int)c));
                    else sb.append(c);
                    break;
            }
            i += 1;
        }
        return sb.toString();
    }
}
