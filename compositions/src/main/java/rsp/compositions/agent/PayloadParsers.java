package rsp.compositions.agent;

import java.util.*;
import java.util.function.Function;

/**
 * Static factories for payload parsing functions used in {@link AgentAction}.
 * <p>
 * Each parser converts raw LLM-produced JSON values (String, Number, List, Map, Boolean, null)
 * into the typed payload expected by the event key. Throws {@link IllegalArgumentException}
 * with a descriptive message on unrecognized input.
 */
public final class PayloadParsers {

    private PayloadParsers() {}

    /**
     * Parses to Integer. Accepts Number and numeric String.
     */
    public static Function<Object, Object> toInteger() {
        return value -> {
            if (value instanceof Integer i) return i;
            if (value instanceof Number num) return num.intValue();
            if (value instanceof String str) {
                try {
                    return Integer.parseInt(str);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                        "Expected Integer, got non-numeric String: '" + str + "'");
                }
            }
            throw new IllegalArgumentException(
                "Expected Integer, got " + typeName(value));
        };
    }

    /**
     * Parses to String. Accepts String and Number (via toString).
     */
    public static Function<Object, Object> toStringPayload() {
        return value -> {
            if (value instanceof String s) return s;
            if (value instanceof Number num) return num.toString();
            throw new IllegalArgumentException(
                "Expected String, got " + typeName(value));
        };
    }

    /**
     * Parses to {@code Set<String>}. Accepts String (wraps in singleton Set),
     * List (copies elements to Set), and Set (passthrough).
     */
    public static Function<Object, Object> toSetOfStrings() {
        return value -> {
            if (value instanceof Set<?> s) return s;
            if (value instanceof String str) return Set.of(str);
            if (value instanceof List<?> list) {
                Set<String> result = new LinkedHashSet<>(list.size());
                for (Object item : list) {
                    if (item instanceof String s) {
                        result.add(s);
                    } else if (item != null) {
                        result.add(item.toString());
                    }
                }
                return Set.copyOf(result);
            }
            throw new IllegalArgumentException(
                "Expected Set<String>, got " + typeName(value));
        };
    }

    /**
     * Parses to {@code Map<String, Object>}. Accepts Map (passthrough).
     */
    public static Function<Object, Object> toMapOfStringObject() {
        return value -> {
            if (value instanceof Map<?, ?> m) return m;
            throw new IllegalArgumentException(
                "Expected Map<String, Object>, got " + typeName(value));
        };
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
