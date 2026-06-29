package rsp.util.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnescapeJsonUtilsTests {

    @Test
    void escapes_string() {
        assertEquals("123 abc XYZ \\n \\t \\u262f", JsonUtils.escape("123 abc XYZ \n \t ☯"));
    }

    @Test
    void escapes_empty_string() {
        assertEquals("", JsonUtils.escape(""));
    }

    @Test
    void unescapes_string() {
        assertEquals("123 abc XYZ \n \t ☯", JsonUtils.unescape("123 abc XYZ \\n \\t \\u262f"));
    }

    @Test
    void unescapes_empty_string() {
        assertEquals("", JsonUtils.unescape(""));
    }

    @Test
    void unescape_keeps_trailing_plain_character() {
        // Regression: the previous implementation stopped at length-1 and dropped the last char.
        assertEquals("abc", JsonUtils.unescape("abc"));
        assertEquals("a\tb", JsonUtils.unescape("a\\tb"));
    }
}
