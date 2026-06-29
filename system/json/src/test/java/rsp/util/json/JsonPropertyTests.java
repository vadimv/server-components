package rsp.util.json;

import org.junit.jupiter.api.Test;
import rsp.pbt.Property;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property-based tests for the parser/writer pair, using the in-house {@link rsp.pbt.Gen}/{@link Property}
 * harness and the generators in {@link JsonGen}.
 */
class JsonPropertyTests {

    @Test
    void parse_after_write_is_the_identity() {
        Property.forAll(JsonGen.values(4)).check(value ->
                assertEquals(value, Json.parse(Json.write(value))));
    }

    @Test
    void write_is_canonical_and_stable() {
        Property.forAll(JsonGen.values(4)).check(value -> {
            final String once = Json.write(value);
            assertEquals(once, Json.write(Json.parse(once)));
        });
    }

    @Test
    void leading_and_trailing_whitespace_is_insignificant() {
        Property.forAll(JsonGen.values(3)).check(value -> {
            final String text = Json.write(value);
            assertEquals(Json.parse(text), Json.parse(" \n\t" + text + "\r\n "));
        });
    }

    @Test
    void arbitrary_input_yields_a_value_or_a_JsonException_never_anything_else() {
        Property.forAll(JsonGen.arbitrary()).check(text -> {
            try {
                Json.parse(text);
            } catch (final JsonDataType.JsonException expected) {
                // The only acceptable failure mode for malformed input.
            }
        });
    }
}
