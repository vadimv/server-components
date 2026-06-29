package rsp.util.json;

import rsp.pbt.Gen;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Generators for property-based tests. The in-house {@link Gen} harness ships only an {@code alpha}
 * (a–z) string generator, so this builds a JSON-grade string generator (escape-triggering and
 * non-ASCII characters, including lone surrogates) and a recursive {@link JsonDataType} generator.
 */
final class JsonGen {

    private JsonGen() {}

    /** Characters that exercise the escaping paths: printable ASCII, the special escapes, and non-ASCII. */
    static Gen<Character> jsonChars() {
        return Gen.oneOf(
                Gen.integers(0x20, 0x7e).map(i -> (char) (int) i),
                Gen.of('"', '\\', '/', '\n', '\r', '\t', '\b', '\f'),
                Gen.integers(0x80, 0xffff).map(i -> (char) (int) i));
    }

    static Gen<String> jsonStrings(final int maxLength) {
        return jsonChars().list(0, maxLength).map(JsonGen::charsToString);
    }

    static Gen<JsonDataType> numbers() {
        return Gen.<JsonDataType>oneOf(
                Gen.integers(-100_000, 100_000).map(JsonDataType.Number::of),
                Gen.longs(Long.MIN_VALUE, Long.MAX_VALUE).map(JsonDataType.Number::of),
                Gen.of("9007199254740993", "-9007199254740993", "1e100", "3.14159265358979",
                                "0", "-0.5", "123456789012345678901234567890")
                        .map(s -> new JsonDataType.Number(new BigDecimal(s))));
    }

    static Gen<JsonDataType> leaf() {
        return Gen.<JsonDataType>oneOf(
                Gen.of(JsonDataType.Null.INSTANCE),
                Gen.booleans().map(JsonDataType.Boolean::new),
                numbers(),
                jsonStrings(8).map(JsonDataType.String::new));
    }

    /** A possibly-nested value, expanded up to {@code depth} levels of arrays/objects. */
    static Gen<JsonDataType> values(final int depth) {
        return Gen.recursive(
                JsonGen::leaf,
                inner -> Gen.<JsonDataType>oneOf(
                        leaf(),
                        inner.list(0, 4).map(JsonGen::toArray),
                        Gen.maps(jsonStrings(6), inner, 4).map(JsonGen::toObject)),
                depth);
    }

    /** Arbitrary (mostly malformed) text built from JSON structural characters, for robustness checks. */
    static Gen<String> arbitrary() {
        final Gen<Character> wild = Gen.oneOf(
                Gen.of('{', '}', '[', ']', '"', ':', ',', '\\', 't', 'f', 'n', 'u', 'e', '0', '1', '.', '-', '+', ' ', '\n'),
                Gen.integers(0x20, 0x7e).map(i -> (char) (int) i));
        return wild.list(0, 24).map(JsonGen::charsToString);
    }

    private static JsonDataType toArray(final List<JsonDataType> elements) {
        return new JsonDataType.Array(elements.toArray(new JsonDataType[0]));
    }

    private static JsonDataType toObject(final Map<String, JsonDataType> entries) {
        return new JsonDataType.Object(entries);
    }

    private static String charsToString(final List<Character> chars) {
        final StringBuilder sb = new StringBuilder(chars.size());
        for (final char c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }
}
