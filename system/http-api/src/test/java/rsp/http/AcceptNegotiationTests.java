package rsp.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class AcceptNegotiationTests {
    private static final String HTML = "text/html; charset=utf-8";
    private static final String JSON = "application/json; charset=utf-8";

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "*/* | html",
            "text/html,application/json | html",
            "application/json | json",
            "text/*;q=0.5,application/json;q=0.8 | json",
            "text/html;q=0,*/*;q=1 | json",
            "application/json;q=0,text/html;q=0,*/*;q=1 | none",
            "text/html;q=0.5,text/*;q=1,application/json;q=0.7 | json",
            "application/json; charset=\"UTF-8\" | json",
            "application/json; charset=iso-8859-1 | none",
            "TEXT/HTML;Q=0.1,APPLICATION/JSON;Q=0.2 | json",
            "image/png | none",
            "*/*;q=0 | none",
            "text/html;q=1.1,application/json | json",
            "text/html;q=oops | none"
    })
    void selectsRepresentations(String accept, String expected) {
        var selected = AcceptNegotiation.select(HttpHeaders.builder().add("Accept", accept).build(), HTML, JSON);
        assertEquals(switch (expected) {
            case "html" -> HTML;
            case "json" -> JSON;
            default -> null;
        }, selected.orElse(null));
    }

    @Test
    void missingEmptyAndRepeatedFieldsHaveDistinctSemantics() {
        assertEquals(HTML, AcceptNegotiation.select(HttpHeaders.EMPTY, HTML, JSON).orElseThrow());
        assertTrue(AcceptNegotiation.select(HttpHeaders.builder().add("Accept", "").build(), HTML, JSON).isEmpty());
        var headers = HttpHeaders.builder().add("Accept", "text/html;q=0.2")
                .add("accept", "application/json;q=0.9").build();
        assertEquals(JSON, AcceptNegotiation.select(headers, HTML, JSON).orElseThrow());
    }

    @Test
    void parameterSpecificityOverridesGenericRangesAndQuotedSeparatorsArePreserved() {
        var headers = HttpHeaders.builder().add("Accept", "text/html;charset=utf-8;q=0,text/html;q=1").build();
        assertTrue(AcceptNegotiation.select(headers, HTML).isEmpty());
        String profiled = "application/json; profile=\"Upper,Case;Value\"";
        headers = HttpHeaders.builder().add("Accept", profiled + ";q=0.9, text/html;q=0.1").build();
        assertEquals(profiled, AcceptNegotiation.select(headers, HTML, profiled).orElseThrow());
        assertTrue(AcceptNegotiation.select(HttpHeaders.builder()
                .add("Accept", "application/json;profile=lower").build(), profiled).isEmpty());
    }
}
