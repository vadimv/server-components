package rsp.compositions.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PayloadParsersTests {

    // --- toInteger ---

    @Test
    void toInteger_from_integer() {
        assertEquals(42, PayloadParsers.toInteger().apply(42));
    }

    @Test
    void toInteger_from_long() {
        assertEquals(42, PayloadParsers.toInteger().apply(42L));
    }

    @Test
    void toInteger_from_double() {
        assertEquals(42, PayloadParsers.toInteger().apply(42.9));
    }

    @Test
    void toInteger_from_string() {
        assertEquals(42, PayloadParsers.toInteger().apply("42"));
    }

    @Test
    void toInteger_from_non_numeric_string_throws() {
        var e = assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toInteger().apply("abc"));
        assertTrue(e.getMessage().contains("non-numeric String"));
    }

    @Test
    void toInteger_from_boolean_throws() {
        var e = assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toInteger().apply(true));
        assertTrue(e.getMessage().contains("Boolean"));
    }

    @Test
    void toInteger_from_list_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toInteger().apply(List.of(1)));
    }

    @Test
    void toInteger_from_null_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toInteger().apply(null));
    }

    // --- toStringPayload ---

    @Test
    void toStringPayload_from_string() {
        assertEquals("hello", PayloadParsers.toStringPayload().apply("hello"));
    }

    @Test
    void toStringPayload_from_number() {
        assertEquals("42", PayloadParsers.toStringPayload().apply(42));
    }

    @Test
    void toStringPayload_from_boolean_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toStringPayload().apply(true));
    }

    @Test
    void toStringPayload_from_null_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toStringPayload().apply(null));
    }

    // --- toSetOfStrings ---

    @Test
    void toSetOfStrings_from_string() {
        assertEquals(Set.of("1"), PayloadParsers.toSetOfStrings().apply("1"));
    }

    @Test
    void toSetOfStrings_from_set() {
        Set<String> input = Set.of("a", "b");
        assertSame(input, PayloadParsers.toSetOfStrings().apply(input));
    }

    @Test
    void toSetOfStrings_from_list() {
        assertEquals(Set.of("a", "b"), PayloadParsers.toSetOfStrings().apply(List.of("a", "b")));
    }

    @Test
    void toSetOfStrings_from_list_with_numbers() {
        assertEquals(Set.of("1", "2"), PayloadParsers.toSetOfStrings().apply(List.of(1, 2)));
    }

    @Test
    void toSetOfStrings_from_integer_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toSetOfStrings().apply(42));
    }

    @Test
    void toSetOfStrings_from_boolean_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toSetOfStrings().apply(true));
    }

    @Test
    void toSetOfStrings_from_null_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toSetOfStrings().apply(null));
    }

    // --- toMapOfStringObject ---

    @Test
    void toMapOfStringObject_from_map() {
        Map<String, Object> input = Map.of("key", "value");
        assertSame(input, PayloadParsers.toMapOfStringObject().apply(input));
    }

    @Test
    void toMapOfStringObject_from_string_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toMapOfStringObject().apply("not a map"));
    }

    @Test
    void toMapOfStringObject_from_list_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toMapOfStringObject().apply(List.of("a")));
    }

    @Test
    void toMapOfStringObject_from_null_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toMapOfStringObject().apply(null));
    }
}
