package rsp.util.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Entry point for JSON parsing and writing.
 *
 * <p>{@link #parse(String)} builds a {@link JsonDataType} tree with a single-pass, hand-written
 * recursive-descent parser; {@link #write(JsonDataType)} serialises one back to compact text.
 * Parsing is strict (RFC 8259): no comments, no trailing commas, no leading zeros, and only
 * whitespace may follow the top-level value, which may itself be any JSON value. Resource use is
 * bounded by {@link JsonLimits}; malformed or over-limit input raises {@link JsonDataType.JsonException}
 * and never an {@link Error}.
 *
 * <p>The facade is stateless and thread-safe; each {@code parse} call uses its own cursor.
 */
public final class Json {

    private Json() {}

    /**
     * Parses JSON text using {@link JsonLimits#DEFAULT}.
     *
     * @param jsonString the JSON text
     * @return the parsed value tree
     * @throws JsonDataType.JsonException if the text is malformed or exceeds a default limit
     */
    public static JsonDataType parse(final String jsonString) {
        return parse(jsonString, JsonLimits.DEFAULT);
    }

    /**
     * Parses JSON text with the given limits.
     *
     * @param jsonString the JSON text
     * @param limits     the resource bounds to enforce
     * @return the parsed value tree
     * @throws JsonDataType.JsonException if the text is malformed or exceeds a limit
     */
    public static JsonDataType parse(final String jsonString, final JsonLimits limits) {
        return new Parser(jsonString, limits).parseDocument();
    }

    /**
     * A {@link JsonParser} using {@link JsonLimits#DEFAULT}.
     *
     * @return a reusable, thread-safe parser
     */
    public static JsonParser parser() {
        return parser(JsonLimits.DEFAULT);
    }

    /**
     * A {@link JsonParser} using the given limits.
     *
     * @param limits the resource bounds to enforce
     * @return a reusable, thread-safe parser
     */
    public static JsonParser parser(final JsonLimits limits) {
        Objects.requireNonNull(limits);
        return jsonString -> parse(jsonString, limits);
    }

    /**
     * Serialises a value tree to compact JSON.
     *
     * @param value the value tree
     * @return the JSON text
     */
    public static String write(final JsonDataType value) {
        return JsonWriter.write(value);
    }

    /**
     * A single-use cursor over the input. Not thread-safe; created fresh per parse.
     */
    private static final class Parser {
        private final String s;
        private final int len;
        private final JsonLimits limits;
        private int pos;
        private int depth;

        Parser(final String s, final JsonLimits limits) {
            this.s = Objects.requireNonNull(s);
            this.limits = Objects.requireNonNull(limits);
            if (s.length() > limits.maxInputLength()) {
                throw new JsonDataType.JsonException("Input length " + s.length()
                        + " exceeds limit " + limits.maxInputLength());
            }
            this.len = s.length();
        }

        JsonDataType parseDocument() {
            skipWhitespace();
            final JsonDataType value = parseValue();
            skipWhitespace();
            if (pos < len) {
                throw error("Unexpected trailing content");
            }
            return value;
        }

        private JsonDataType parseValue() {
            if (pos >= len) {
                throw error("Unexpected end of input");
            }
            final char c = s.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> new JsonDataType.String(parseString());
                case 't' -> literal("true", new JsonDataType.Boolean(true));
                case 'f' -> literal("false", new JsonDataType.Boolean(false));
                case 'n' -> literal("null", JsonDataType.Null.INSTANCE);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield parseNumber();
                    }
                    throw error("Unexpected character '" + c + "'");
                }
            };
        }

        private JsonDataType parseObject() {
            pos++; // consume '{'
            enter();
            final Map<String, JsonDataType> values = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                exit();
                return new JsonDataType.Object(values);
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("Expected a string key");
                }
                final String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                values.put(key, parseValue());
                if (values.size() > limits.maxObjectEntries()) {
                    throw error("Object entry count exceeds limit " + limits.maxObjectEntries());
                }
                skipWhitespace();
                final char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    break;
                } else {
                    throw error("Expected ',' or '}'");
                }
            }
            exit();
            return new JsonDataType.Object(values);
        }

        private JsonDataType parseArray() {
            pos++; // consume '['
            enter();
            final List<JsonDataType> elements = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                exit();
                return new JsonDataType.Array();
            }
            while (true) {
                skipWhitespace();
                elements.add(parseValue());
                if (elements.size() > limits.maxArrayElements()) {
                    throw error("Array element count exceeds limit " + limits.maxArrayElements());
                }
                skipWhitespace();
                final char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    break;
                } else {
                    throw error("Expected ',' or ']'");
                }
            }
            exit();
            return new JsonDataType.Array(elements.toArray(new JsonDataType[0]));
        }

        /** Parses a string starting at the opening quote, returning the unescaped value. */
        private String parseString() {
            pos++; // consume opening '"'
            final int start = pos;
            // Fast path: no escapes -> a single substring, no StringBuilder.
            while (pos < len) {
                final char c = s.charAt(pos);
                if (c == '"') {
                    final String value = s.substring(start, pos);
                    pos++;
                    checkStringLength(value.length());
                    return value;
                }
                if (c == '\\') {
                    break;
                }
                if (c < 0x20) {
                    throw error("Unescaped control character U+" + Integer.toHexString(c));
                }
                pos++;
            }
            // Slow path: at least one escape.
            final StringBuilder sb = new StringBuilder(len - start);
            sb.append(s, start, pos);
            while (pos < len) {
                final char c = s.charAt(pos);
                if (c == '"') {
                    pos++;
                    checkStringLength(sb.length());
                    return sb.toString();
                }
                if (c == '\\') {
                    pos++;
                    appendEscape(sb);
                } else if (c < 0x20) {
                    throw error("Unescaped control character U+" + Integer.toHexString(c));
                } else {
                    sb.append(c);
                    pos++;
                }
            }
            throw error("Unterminated string");
        }

        private void appendEscape(final StringBuilder sb) {
            if (pos >= len) {
                throw error("Unterminated escape sequence");
            }
            final char e = s.charAt(pos);
            switch (e) {
                case '"' -> { sb.append('"'); pos++; }
                case '\\' -> { sb.append('\\'); pos++; }
                case '/' -> { sb.append('/'); pos++; }
                case 'b' -> { sb.append('\b'); pos++; }
                case 'f' -> { sb.append('\f'); pos++; }
                case 'n' -> { sb.append('\n'); pos++; }
                case 'r' -> { sb.append('\r'); pos++; }
                case 't' -> { sb.append('\t'); pos++; }
                case 'u' -> {
                    if (pos + 5 > len) {
                        throw error("Truncated \\u escape");
                    }
                    int v = 0;
                    for (int i = 1; i <= 4; i++) {
                        v = (v << 4) | hexDigit(s.charAt(pos + i));
                    }
                    // Append the UTF-16 code unit directly; a high+low surrogate pair from two
                    // consecutive \\u escapes naturally forms the astral code point in the String.
                    sb.append((char) v);
                    pos += 5;
                }
                default -> throw error("Invalid escape '\\" + e + "'");
            }
        }

        private int hexDigit(final char c) {
            if (c >= '0' && c <= '9') return c - '0';
            if (c >= 'a' && c <= 'f') return c - 'a' + 10;
            if (c >= 'A' && c <= 'F') return c - 'A' + 10;
            throw error("Invalid hex digit '" + c + "'");
        }

        private JsonDataType parseNumber() {
            final int start = pos;
            int mantissaDigits = 0;
            if (peek() == '-') {
                pos++;
            }
            // Integer part: a single 0, or 1-9 followed by digits (no leading zeros).
            char c = require("a digit");
            if (c == '0') {
                pos++;
                mantissaDigits++;
            } else if (c >= '1' && c <= '9') {
                while (pos < len && isDigit(s.charAt(pos))) {
                    pos++;
                    mantissaDigits++;
                }
            } else {
                throw error("Invalid number");
            }
            // Fractional part.
            if (pos < len && s.charAt(pos) == '.') {
                pos++;
                if (pos >= len || !isDigit(s.charAt(pos))) {
                    throw error("Expected a digit after the decimal point");
                }
                while (pos < len && isDigit(s.charAt(pos))) {
                    pos++;
                    mantissaDigits++;
                }
            }
            // Exponent part.
            if (pos < len && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                pos++;
                if (pos < len && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                    pos++;
                }
                final int expStart = pos;
                if (pos >= len || !isDigit(s.charAt(pos))) {
                    throw error("Expected a digit in the exponent");
                }
                while (pos < len && isDigit(s.charAt(pos))) {
                    pos++;
                }
                checkExponent(expStart, pos);
            }
            if (mantissaDigits > limits.maxNumberDigits()) {
                throw error("Number has " + mantissaDigits + " digits, exceeding limit "
                        + limits.maxNumberDigits());
            }
            return new JsonDataType.Number(new BigDecimal(s.substring(start, pos)));
        }

        /** Rejects an exponent whose magnitude exceeds the limit, without overflowing. */
        private void checkExponent(final int digitsStart, final int digitsEnd) {
            int i = digitsStart;
            while (i < digitsEnd - 1 && s.charAt(i) == '0') {
                i++; // skip leading zeros so the digit count reflects the magnitude
            }
            final int digits = digitsEnd - i;
            // The limit is an int (<= 10 digits); anything longer certainly exceeds it.
            if (digits > 10 || Long.parseLong(s, i, digitsEnd, 10) > limits.maxNumberExponent()) {
                throw error("Number exponent magnitude exceeds limit " + limits.maxNumberExponent());
            }
        }

        private JsonDataType literal(final String word, final JsonDataType value) {
            if (pos + word.length() > len || !s.regionMatches(pos, word, 0, word.length())) {
                throw error("Invalid literal, expected '" + word + "'");
            }
            pos += word.length();
            return value;
        }

        private void enter() {
            if (++depth > limits.maxDepth()) {
                throw error("Nesting depth exceeds limit " + limits.maxDepth());
            }
        }

        private void exit() {
            depth--;
        }

        private void checkStringLength(final int length) {
            if (length > limits.maxStringLength()) {
                throw error("String length " + length + " exceeds limit " + limits.maxStringLength());
            }
        }

        private void skipWhitespace() {
            while (pos < len) {
                final char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private static boolean isDigit(final char c) {
            return c >= '0' && c <= '9';
        }

        private char peek() {
            if (pos >= len) {
                throw error("Unexpected end of input");
            }
            return s.charAt(pos);
        }

        private char require(final String what) {
            if (pos >= len) {
                throw error("Unexpected end of input, expected " + what);
            }
            return s.charAt(pos);
        }

        private void expect(final char c) {
            if (pos >= len || s.charAt(pos) != c) {
                throw error("Expected '" + c + "'");
            }
            pos++;
        }

        private JsonDataType.JsonException error(final String message) {
            int line = 1;
            int col = 1;
            for (int i = 0; i < pos && i < len; i++) {
                if (s.charAt(i) == '\n') {
                    line++;
                    col = 1;
                } else {
                    col++;
                }
            }
            return new JsonDataType.JsonException(message + " at line " + line + ", column " + col
                    + " (offset " + pos + ")");
        }
    }
}
