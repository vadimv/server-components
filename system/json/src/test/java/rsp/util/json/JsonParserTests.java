package rsp.util.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec-conformance corpus for {@link Json#parse}: valid inputs map to the right tree, and a curated
 * set of invalid inputs (in the spirit of Seriot's JSONTestSuite {@code n_} cases) is rejected with
 * {@link JsonDataType.JsonException}.
 */
class JsonParserTests {

    // --- literals & scalars (top-level values are allowed) -------------------------------------

    @Test
    void parses_literals() {
        assertEquals(new JsonDataType.Boolean(true), Json.parse("true"));
        assertEquals(new JsonDataType.Boolean(false), Json.parse("false"));
        assertEquals(JsonDataType.Null.INSTANCE, Json.parse("null"));
    }

    @Test
    void parses_top_level_string_and_number() {
        assertEquals(new JsonDataType.String("hello"), Json.parse("\"hello\""));
        assertEquals(JsonDataType.Number.of(42), Json.parse("42"));
    }

    // --- numbers ------------------------------------------------------------------------------

    @Test
    void parses_number_forms() {
        assertEquals(new JsonDataType.Number(new BigDecimal("0")), Json.parse("0"));
        assertEquals(new JsonDataType.Number(new BigDecimal("-0")), Json.parse("-0"));
        assertEquals(new JsonDataType.Number(new BigDecimal("3.14")), Json.parse("3.14"));
        assertEquals(new JsonDataType.Number(new BigDecimal("-12.5")), Json.parse("-12.5"));
        assertEquals(new JsonDataType.Number(new BigDecimal("6.022e23")), Json.parse("6.022e23"));
        assertEquals(new JsonDataType.Number(new BigDecimal("1E-7")), Json.parse("1e-7"));
    }

    @Test
    void preserves_big_integer_precision_beyond_double() {
        // 2^53 + 1 is not representable as a double; BigDecimal keeps it exact.
        final JsonDataType v = Json.parse("9007199254740993");
        assertEquals(new BigDecimal("9007199254740993"), ((JsonDataType.Number) v).value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "-01", "1.", ".5", "+1", "1e", "1e+", "--1", "1.2.3", "0x10", "Infinity", "NaN", "1,"})
    void rejects_malformed_numbers(final String json) {
        assertThrows(JsonDataType.JsonException.class, () -> Json.parse(json));
    }

    // --- strings & escapes --------------------------------------------------------------------

    @Test
    void parses_escapes() {
        assertEquals(new JsonDataType.String("a\nb"), Json.parse("\"a\\nb\""));
        assertEquals(new JsonDataType.String("tab\there"), Json.parse("\"tab\\there\""));
        assertEquals(new JsonDataType.String("quote\"slash\\fwd/"), Json.parse("\"quote\\\"slash\\\\fwd\\/\""));
        assertEquals(new JsonDataType.String("☯"), Json.parse("\"\\u262f\""));
    }

    @Test
    void parses_surrogate_pair() {
        // U+1F600 GRINNING FACE encoded as a UTF-16 surrogate pair.
        assertEquals(new JsonDataType.String("😀"), Json.parse("\"\\uD83D\\uDE00\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"unterminated", "\"bad\\xescape\"", "\"\\u12\"", "\"lone\\\""})
    void rejects_malformed_strings(final String json) {
        assertThrows(JsonDataType.JsonException.class, () -> Json.parse(json));
    }

    @Test
    void rejects_unescaped_control_character_in_string() {
        assertThrows(JsonDataType.JsonException.class, () -> Json.parse("\"a\tb\""));
    }

    // --- arrays & objects ----------------------------------------------------------------------

    @Test
    void parses_empty_and_nested_containers() {
        assertEquals(JsonDataType.Object.EMPTY, Json.parse("{}"));
        assertEquals(new JsonDataType.Array(), Json.parse("[]"));
        assertEquals(new JsonDataType.Array(JsonDataType.Number.of(1), JsonDataType.Number.of(2)),
                Json.parse("[1, 2]"));
    }

    @Test
    void parses_object() {
        final JsonDataType.Object expected = new JsonDataType.Object(Map.of(
                "a", JsonDataType.Number.of(1),
                "b", new JsonDataType.String("two")));
        assertEquals(expected, Json.parse("{\"a\": 1, \"b\": \"two\"}"));
    }

    @Test
    void duplicate_keys_last_wins() {
        final JsonDataType v = Json.parse("{\"k\": 1, \"k\": 2}");
        assertEquals(JsonDataType.Number.of(2), ((JsonDataType.Object) v).value("k"));
    }

    @Test
    void tolerates_insignificant_whitespace() {
        assertEquals(Json.parse("{\"a\":[1,2]}"),
                Json.parse("  {  \"a\" : [ 1 , 2 ]  }\n\t"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{,}", "[1,]", "{\"a\":1,}", "[1 2]", "{\"a\" 1}", "{a:1}", "", "  ", "1 2", "{\"a\":1}x"})
    void rejects_structural_errors(final String json) {
        assertThrows(JsonDataType.JsonException.class, () -> Json.parse(json));
    }

    @Test
    void rejects_comments() {
        assertThrows(JsonDataType.JsonException.class, () -> Json.parse("{} // trailing"));
        assertThrows(JsonDataType.JsonException.class, () -> Json.parse("/* c */ 1"));
    }
}
