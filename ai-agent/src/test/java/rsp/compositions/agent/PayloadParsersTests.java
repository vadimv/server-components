package rsp.compositions.agent;

import rsp.compositions.contract.AgentPayload;
import rsp.compositions.contract.PayloadParsers;


import org.junit.jupiter.api.Test;
import rsp.util.json.JsonDataType;

import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PayloadParsersTests {

    // --- toInteger ---

    @Test
    void toInteger_from_integer() {
        assertEquals(42, PayloadParsers.toInteger().apply(AgentPayload.of(42)));
    }

    @Test
    void toInteger_from_long() {
        assertEquals(42, PayloadParsers.toInteger().apply(AgentPayload.of(42L)));
    }

    @Test
    void toInteger_from_double() {
        assertEquals(42, PayloadParsers.toInteger().apply(AgentPayload.of(42.9)));
    }

    @Test
    void toInteger_from_string() {
        assertEquals(42, PayloadParsers.toInteger().apply(AgentPayload.of("42")));
    }

    @Test
    void toInteger_from_non_numeric_string_throws() {
        var e = assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toInteger().apply(AgentPayload.of("abc")));
        assertTrue(e.getMessage().contains("non-numeric String"));
    }

    @Test
    void toInteger_from_boolean_throws() {
        var e = assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toInteger().apply(AgentPayload.of(true)));
        assertTrue(e.getMessage().contains("Boolean"));
    }

    @Test
    void toInteger_from_array_throws() {
        AgentPayload payload = new AgentPayload(new JsonDataType.Array(JsonDataType.Number.of(1)));
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toInteger().apply(payload));
    }

    @Test
    void toInteger_from_null_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toInteger().apply(AgentPayload.EMPTY));
    }

    // --- toStringPayload ---

    @Test
    void toStringPayload_from_string() {
        assertEquals("hello", PayloadParsers.toStringPayload().apply(AgentPayload.of("hello")));
    }

    @Test
    void toStringPayload_from_number() {
        assertEquals("42", PayloadParsers.toStringPayload().apply(AgentPayload.of(42)));
    }

    @Test
    void toStringPayload_from_boolean_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toStringPayload().apply(AgentPayload.of(true)));
    }

    @Test
    void toStringPayload_from_null_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toStringPayload().apply(AgentPayload.EMPTY));
    }

    // --- toSetOfStrings ---

    @Test
    void toSetOfStrings_from_string() {
        assertEquals(Set.of("1"), PayloadParsers.toSetOfStrings().apply(AgentPayload.of("1")));
    }

    @Test
    void toSetOfStrings_from_array() {
        AgentPayload payload = new AgentPayload(new JsonDataType.Array(
            new JsonDataType.String("a"), new JsonDataType.String("b")));
        assertEquals(Set.of("a", "b"), PayloadParsers.toSetOfStrings().apply(payload));
    }

    @Test
    void toSetOfStrings_from_array_with_numbers() {
        AgentPayload payload = new AgentPayload(new JsonDataType.Array(
            JsonDataType.Number.of(1), JsonDataType.Number.of(2)));
        assertEquals(Set.of("1", "2"), PayloadParsers.toSetOfStrings().apply(payload));
    }

    @Test
    void toSetOfStrings_from_number_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toSetOfStrings().apply(AgentPayload.of(42)));
    }

    @Test
    void toSetOfStrings_from_boolean_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toSetOfStrings().apply(AgentPayload.of(true)));
    }

    @Test
    void toSetOfStrings_from_null_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toSetOfStrings().apply(AgentPayload.EMPTY));
    }

    // --- toMapOfStringObject ---

    @Test
    void toMapOfStringObject_from_object() {
        AgentPayload payload = new AgentPayload(
            new JsonDataType.Object(Map.of("key", new JsonDataType.String("value"))));
        Map<String, Object> result = (Map<String, Object>) PayloadParsers.toMapOfStringObject().apply(payload);
        assertEquals(Map.of("key", "value"), result);
    }

    @Test
    void toMapOfStringObject_from_string_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toMapOfStringObject().apply(AgentPayload.of("not a map")));
    }

    @Test
    void toMapOfStringObject_from_array_throws() {
        AgentPayload payload = new AgentPayload(new JsonDataType.Array(new JsonDataType.String("a")));
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toMapOfStringObject().apply(payload));
    }

    @Test
    void toMapOfStringObject_from_null_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParsers.toMapOfStringObject().apply(AgentPayload.EMPTY));
    }
}
