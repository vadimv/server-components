package rsp.util.json;

import java.util.Map;

/**
 * Serialises a {@link JsonDataType} tree to compact JSON text.
 *
 * <p>String values and object keys are escaped (control characters and non-ASCII as {@code \\uXXXX}).
 * Numbers are emitted with {@link java.math.BigDecimal#toString()} — deliberately <em>not</em>
 * {@code toPlainString()}: {@code toString()} keeps scientific notation, so a value such as
 * {@code 1E+1000000000} stays a few characters instead of expanding to a gigabyte of digits. Since
 * {@code BigDecimal} can never be {@code NaN}/{@code Infinity}, the output is always valid JSON.
 */
public final class JsonWriter {

    private JsonWriter() {}

    /**
     * Serialises {@code value} to compact JSON.
     *
     * @param value the value tree
     * @return the JSON text
     */
    public static String write(final JsonDataType value) {
        final StringBuilder sb = new StringBuilder();
        append(sb, value);
        return sb.toString();
    }

    private static void append(final StringBuilder sb, final JsonDataType value) {
        switch (value) {
            case JsonDataType.Null ignored -> sb.append("null");
            case JsonDataType.Boolean b -> sb.append(b.value() ? "true" : "false");
            case JsonDataType.Number n -> sb.append(n.value().toString());
            case JsonDataType.String s -> appendString(sb, s.value());
            case JsonDataType.Array a -> appendArray(sb, a);
            case JsonDataType.Object o -> appendObject(sb, o);
        }
    }

    private static void appendArray(final StringBuilder sb, final JsonDataType.Array array) {
        sb.append('[');
        final JsonDataType[] elements = array.elements();
        for (int i = 0; i < elements.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            append(sb, elements[i]);
        }
        sb.append(']');
    }

    private static void appendObject(final StringBuilder sb, final JsonDataType.Object object) {
        sb.append('{');
        boolean first = true;
        for (final Map.Entry<String, JsonDataType> e : object.asMap().entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            appendString(sb, e.getKey());
            sb.append(':');
            append(sb, e.getValue());
        }
        sb.append('}');
    }

    private static void appendString(final StringBuilder sb, final String s) {
        sb.append('"');
        appendEscaped(sb, s);
        sb.append('"');
    }

    /**
     * Appends {@code s} with JSON string escaping (without surrounding quotes). Control characters
     * and non-ASCII characters are emitted as {@code \\uXXXX}.
     *
     * @param sb the target buffer
     * @param s  the raw string
     */
    static void appendEscaped(final StringBuilder sb, final String s) {
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ' || c > '~') {
                        sb.append("\\u");
                        final String hex = Integer.toHexString(c);
                        sb.append("0000", 0, 4 - hex.length()).append(hex);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
    }

    /**
     * Escapes a string the way it would appear inside JSON quotes (control + non-ASCII as
     * {@code \\uXXXX}), without the surrounding quotes.
     *
     * @param s the raw string
     * @return the escaped string
     */
    public static String escape(final String s) {
        final StringBuilder sb = new StringBuilder(s.length());
        appendEscaped(sb, s);
        return sb.toString();
    }
}
