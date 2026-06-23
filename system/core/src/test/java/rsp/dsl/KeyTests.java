package rsp.dsl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeyTests {

    @Test
    void numeric_key_uses_kn_prefix_and_decimal() {
        assertEquals("kn42", Key.of(42L).segment());
        assertEquals("kn0", Key.of(0L).segment());
        assertEquals("kn-7", Key.of(-7L).segment());
        assertEquals("kn9223372036854775807", Key.of(Long.MAX_VALUE).segment());
    }

    @Test
    void string_key_uses_ks_prefix() {
        assertEquals("ksuserId-42", Key.of("userId-42").segment());
        assertEquals("ks", Key.of("").segment());
    }

    @Test
    void string_key_escapes_path_separator_and_escape_char() {
        assertEquals("ksa%5Fb", Key.of("a_b").segment());
        assertEquals("ks100%25", Key.of("100%").segment());
        assertEquals("ks%25%5F%25", Key.of("%_%").segment());
    }

    @Test
    void escaping_is_unambiguous_between_distinct_inputs() {
        assertNotEquals(Key.of("a_b").segment(), Key.of("a%5Fb").segment());
        assertEquals("ksa%5Fb", Key.of("a_b").segment());
        assertEquals("ksa%255Fb", Key.of("a%5Fb").segment());
    }

    @Test
    void numeric_and_string_keys_never_collide() {
        assertNotEquals(Key.of(42L).segment(), Key.of("42").segment());
        assertEquals("kn42", Key.of(42L).segment());
        assertEquals("ks42", Key.of("42").segment());
    }

    @Test
    void segment_never_contains_unescaped_path_separator() {
        for (final String value : new String[]{"a_b", "_", "__", "a_b_c", "x%5Fy"}) {
            final String stringSeg = Key.of(value).segment();
            assertFalse(stringSeg.substring(2).contains("_"),
                    "string key segment must not contain a raw separator: " + stringSeg);
        }
    }

    @Test
    void escapes_html_and_quote_characters_so_keys_cannot_inject() {
        // A key derived from untrusted input must not be able to break out of data-rsp-key="..."
        assertEquals("ks%22%3E%3Cimg%20src%3Dx%3E", Key.of("\"><img src=x>").segment());
        assertEquals("ks%26", Key.of("&").segment());
        assertEquals("ks%27", Key.of("'").segment());
        assertEquals("ks%3C%3E", Key.of("<>").segment());
    }

    @Test
    void segment_contains_only_attribute_and_wire_safe_characters() {
        final String[] hostile = {"\"><script>", "a&b", "x=y", "sp ace", "tab\tend", "ünîcødé", "a_b%c\""};
        for (final String value : hostile) {
            final String seg = Key.of(value).segment();
            assertTrue(seg.matches("ks[A-Za-z0-9._%-]*"),
                    "segment must be attribute/wire-safe but was: " + seg + " (from: " + value + ")");
        }
    }

    @Test
    void rejects_null_string_key() {
        assertThrows(NullPointerException.class, () -> Key.of((String) null));
    }

    @Test
    void html_dsl_factories_match_key_of() {
        assertEquals(Key.of(7L).segment(), rsp.dsl.Html.key(7L).segment());
        assertEquals(Key.of("post-7").segment(), rsp.dsl.Html.key("post-7").segment());
    }
}
