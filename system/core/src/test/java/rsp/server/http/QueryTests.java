package rsp.server.http;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueryTests {

    @Test
    void should_parse_simple_query_string() {
        final Query query = Query.of("key=value");
        assertEquals("value", query.parameterValue("key"));
    }

    @Test
    void should_parse_encoded_query_string() {
        final Query query = Query.of("key=hello%20world");
        assertEquals("hello world", query.parameterValue("key"));
    }

    @Test
    void should_parse_multiple_parameters() {
        final Query query = Query.of("a=1&b=2");
        assertEquals("1", query.parameterValue("a"));
        assertEquals("2", query.parameterValue("b"));
    }

    @Test
    void should_parse_empty_value() {
        final Query query = Query.of("key=");
        assertEquals("", query.parameterValue("key"));
    }

    @Test
    void should_parse_key_only() {
        final Query query = Query.of("key");
        assertEquals("", query.parameterValue("key"));
    }

    @Test
    void should_handle_question_mark_prefix() {
        final Query query = Query.of("?key=value");
        assertEquals("value", query.parameterValue("key"));
    }

    @Test
    void should_encode_on_to_string() {
        final Query query = new Query(List.of(new Query.Parameter("key", "hello world")));
        // URLEncoder encodes space as '+'
        assertEquals("?key=hello+world", query.toString());
    }

    @Test
    void should_encode_special_characters() {
        final Query query = new Query(List.of(new Query.Parameter("key", "a&b=c")));
        assertEquals("?key=a%26b%3Dc", query.toString());
    }

    @Test
    void should_round_trip_complex_values() {
        final String originalValue = "hello world & goodbye = ? /";
        final Query original = new Query(List.of(new Query.Parameter("q", originalValue)));
        
        final String encoded = original.toString();
        final Query parsed = Query.of(encoded);
        
        assertEquals(originalValue, parsed.parameterValue("q"));
    }
    
    @Test
    void should_return_empty_string_for_empty_query() {
        assertEquals("", Query.EMPTY.toString());
    }
}
