package rsp.util.json;

/**
 * Backwards-compatible facade over {@link Json} and {@link JsonWriter}. New code should prefer
 * {@link Json} directly; this type is retained for existing callers.
 */
public final class JsonUtils {

    private JsonUtils() {}

    /**
     * A {@link JsonParser} using {@link JsonLimits#DEFAULT}.
     *
     * @return a reusable, thread-safe parser
     */
    public static JsonParser createParser() {
        return Json.parser();
    }

    /**
     * Parses JSON text using {@link JsonLimits#DEFAULT}.
     *
     * @param s the JSON text
     * @return the parsed value tree
     * @throws JsonDataType.JsonException if the text is malformed or exceeds a default limit
     */
    public static JsonDataType parse(final String s) {
        return Json.parse(s);
    }

    /**
     * Escapes a string as it would appear inside JSON quotes (control and non-ASCII characters as
     * {@code \\uXXXX}), without the surrounding quotes.
     *
     * @param s the raw string
     * @return the escaped string
     */
    public static String escape(final String s) {
        return JsonWriter.escape(s);
    }

    /**
     * Resolves JSON escape sequences in {@code s}. Lenient: an unknown escape passes its character
     * through, and a dangling backslash is kept literally.
     *
     * @param s the escaped string (without surrounding quotes)
     * @return the unescaped string
     */
    public static String unescape(final String s) {
        final StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        final int n = s.length();
        while (i < n) {
            final char c = s.charAt(i);
            if (c != '\\') {
                sb.append(c);
                i++;
                continue;
            }
            if (i + 1 >= n) {
                sb.append('\\');
                i++;
                continue;
            }
            final char e = s.charAt(i + 1);
            switch (e) {
                case '\\' -> { sb.append('\\'); i += 2; }
                case '"' -> { sb.append('"'); i += 2; }
                case '/' -> { sb.append('/'); i += 2; }
                case 'b' -> { sb.append('\b'); i += 2; }
                case 'f' -> { sb.append('\f'); i += 2; }
                case 'n' -> { sb.append('\n'); i += 2; }
                case 'r' -> { sb.append('\r'); i += 2; }
                case 't' -> { sb.append('\t'); i += 2; }
                case 'u' -> {
                    if (i + 6 <= n) {
                        sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                        i += 6;
                    } else {
                        sb.append(e);
                        i += 2;
                    }
                }
                default -> { sb.append(e); i += 2; }
            }
        }
        return sb.toString();
    }
}
