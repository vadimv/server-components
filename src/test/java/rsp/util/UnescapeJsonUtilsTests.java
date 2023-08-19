package rsp.util;

import org.junit.jupiter.api.Test;
import rsp.util.json.JsonUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class UnescapeJsonUtilsTests {

    @Test
    public void should_correctly_escape_string() {
        final String result = JsonUtils.escape("123 abc XYZ \n \t ☯" );
        assertEquals("123 abc XYZ \\n \\t \\u262f", result);
    }

    @Test
    public void should_correctly_escape_empty_string() {
        final String result = JsonUtils.escape("" );
        assertEquals("", result);
    }

    @Test
    public void should_correctly_unescape_string() {
        final String result = JsonUtils.unescape("123 abc XYZ \\n \\t \\u262f");
        assertEquals("123 abc XYZ \n \t ☯", result);
    }

    @Test
    public void should_correctly_unescape_empty_string() {
        final String result = JsonUtils.unescape("");
        assertEquals("", result);
    }
}
