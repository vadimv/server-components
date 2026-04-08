package rsp.compositions.agent;

import rsp.util.json.JsonDataType;

import java.util.*;
import java.util.function.Function;

/**
 * Static factories for payload parsing functions used in {@link PayloadSchemas}.
 * <p>
 * Each parser converts an {@link AgentPayload} (wrapping a {@link JsonDataType})
 * into the typed payload expected by the event key. Throws {@link IllegalArgumentException}
 * with a descriptive message on unrecognized input.
 */
public final class PayloadParsers {

    private PayloadParsers() {}

    /**
     * Parses to Integer. Accepts Number and numeric String.
     */
    public static Function<AgentPayload, Object> toInteger() {
        return payload -> {
            JsonDataType value = payload.value();
            if (value instanceof JsonDataType.Number n) {
                return (int) n.asLong();
            }
            if (value instanceof JsonDataType.String s) {
                try {
                    return Integer.parseInt(s.value());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                        "Expected Integer, got non-numeric String: '" + s.value() + "'");
                }
            }
            throw new IllegalArgumentException(
                "Expected Integer, got " + jsonTypeName(value));
        };
    }

    /**
     * Parses to String. Accepts String and Number (via toString).
     */
    public static Function<AgentPayload, Object> toStringPayload() {
        return payload -> {
            JsonDataType value = payload.value();
            if (value instanceof JsonDataType.String s) return s.value();
            if (value instanceof JsonDataType.Number n) return n.toString();
            throw new IllegalArgumentException(
                "Expected String, got " + jsonTypeName(value));
        };
    }

    /**
     * Parses to {@code Set<String>}. Accepts String (wraps in singleton Set)
     * and Array (copies elements to Set).
     */
    public static Function<AgentPayload, Object> toSetOfStrings() {
        return payload -> {
            JsonDataType value = payload.value();
            if (value instanceof JsonDataType.String s) return Set.of(s.value());
            if (value instanceof JsonDataType.Array array) {
                Set<String> result = new LinkedHashSet<>(array.size());
                for (int i = 0; i < array.size(); i++) {
                    JsonDataType item = array.get(i);
                    if (item instanceof JsonDataType.String s) {
                        result.add(s.value());
                    } else if (item instanceof JsonDataType.Number n) {
                        result.add(n.toString());
                    }
                }
                return Set.copyOf(result);
            }
            throw new IllegalArgumentException(
                "Expected Set<String>, got " + jsonTypeName(value));
        };
    }

    /**
     * Parses to {@code Map<String, Object>}. Accepts JSON Object.
     */
    public static Function<AgentPayload, Object> toMapOfStringObject() {
        return payload -> {
            JsonDataType value = payload.value();
            if (value instanceof JsonDataType.Object obj) {
                return unwrapObject(obj);
            }
            throw new IllegalArgumentException(
                "Expected Map<String, Object>, got " + jsonTypeName(value));
        };
    }

    /**
     * Recursively converts a {@link JsonDataType} to a plain Java value.
     */
    static Object unwrap(JsonDataType value) {
        return switch (value) {
            case JsonDataType.String s -> s.value();
            case JsonDataType.Number n -> n.isFractional() ? n.value() : (int) n.asLong();
            case JsonDataType.Boolean b -> b.value();
            case JsonDataType.Array a -> {
                List<Object> list = new ArrayList<>(a.size());
                for (int i = 0; i < a.size(); i++) {
                    list.add(unwrap(a.get(i)));
                }
                yield List.copyOf(list);
            }
            case JsonDataType.Object o -> unwrapObject(o);
            case JsonDataType.Null _ -> null;
        };
    }

    private static Map<String, Object> unwrapObject(JsonDataType.Object obj) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (java.lang.String key : obj.keys()) {
            Object v = unwrap(obj.value(key));
            if (v != null) {
                result.put(key, v);
            }
        }
        return Map.copyOf(result);
    }

    private static String jsonTypeName(JsonDataType value) {
        if (value == null || value instanceof JsonDataType.Null) return "null";
        return value.getClass().getSimpleName();
    }
}
