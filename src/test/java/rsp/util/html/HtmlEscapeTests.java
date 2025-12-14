package rsp.util.html;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for {@link HtmlEscape}.
 * 
 * This test suite verifies that the HTML escaping utility correctly handles
 * all special characters, edge cases, and security-critical scenarios.
 */
public class HtmlEscapeTests {

    @Nested
    public class SingleCharacterTests {

        @Test
        void escapes_ampersand() {
            assertEquals("&amp;", HtmlEscape.escape("&"));
        }

        @Test
        void escapes_less_than() {
            assertEquals("&lt;", HtmlEscape.escape("<"));
        }

        @Test
        void escapes_greater_than() {
            assertEquals("&gt;", HtmlEscape.escape(">"));
        }

        @Test
        void escapes_double_quote() {
            assertEquals("&quot;", HtmlEscape.escape("\""));
        }

        @Test
        void escapes_single_quote() {
            assertEquals("&#39;", HtmlEscape.escape("'"));
        }
    }

    @Nested
    public class MultipleCharacterTests {

        @Test
        void escapes_multiple_same_characters() {
            assertEquals("&lt;&lt;&lt;", HtmlEscape.escape("<<<"));
        }

        @Test
        void escapes_mixed_special_characters() {
            assertEquals("&lt;&gt;&quot;&amp;&#39;", HtmlEscape.escape("<>\"&'"));
        }

        @Test
        void escapes_all_special_charactersInSequence() {
            assertEquals("&amp;&lt;&gt;&quot;&#39;", HtmlEscape.escape("&<>\"'"));
        }
    }

    @Nested
    public class PlainTextTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "Hello World",
            "Simple text",
            "123456789",
            "email@example.com",
            "https://example.com/path",
            "Text with spaces and punctuation!",
            "CamelCaseText",
            "snake_case_text",
            "kebab-case-text"
        })
        void leaves_plain_text_unchanged(final String text) {
            assertEquals(text, HtmlEscape.escape(text));
        }
    }

    @Nested
    public class SecurityVulnerabilityTests {

        @Test
        void prevents_script_tag_injection() {
            final String input = "<script>alert('XSS')</script>";
            final String escaped = HtmlEscape.escape(input);
            assertEquals("&lt;script&gt;alert(&#39;XSS&#39;)&lt;/script&gt;", escaped);
            assertFalse(escaped.contains("<script>"));
        }

        @Test
        void prevents_event_handler_injection() {
            final String input = "<img src=x onerror=\"alert('XSS')\">";
            final String escaped = HtmlEscape.escape(input);
            assertEquals("&lt;img src=x onerror=&quot;alert(&#39;XSS&#39;)&quot;&gt;", escaped);
            // Verify tag is escaped so it won't be executed as HTML
            assertFalse(escaped.contains("<img"));
            assertTrue(escaped.contains("&lt;img"));
        }

        @Test
        void prevents_attribute_breakout_with_single_quote() {
            final String input = "' onclick='alert(1)'";
            final String escaped = HtmlEscape.escape(input);
            assertEquals("&#39; onclick=&#39;alert(1)&#39;", escaped);
            assertFalse(escaped.contains("' onclick"));
        }

        @Test
        void prevents_attribute_breakout_with_double_quotes() {
            final String input = "\" onclick=\"alert(1)\"";
            final String escaped = HtmlEscape.escape(input);
            assertEquals("&quot; onclick=&quot;alert(1)&quot;", escaped);
            assertFalse(escaped.contains("\" onclick"));
        }

        @Test
        void prevents_html_tag_injection() {
            final String input = "</div><div onclick=\"alert('XSS')\">";
            final String escaped = HtmlEscape.escape(input);
            assertEquals("&lt;/div&gt;&lt;div onclick=&quot;alert(&#39;XSS&#39;)&quot;&gt;", escaped);
            assertFalse(escaped.contains("</div>"));
        }

        @Test
        void prevents_entity_based_attacks() {
            final String input = "&lt;script&gt;";
            final String escaped = HtmlEscape.escape(input);
            // & must be escaped first, so &lt; becomes &amp;lt;
            assertEquals("&amp;lt;script&amp;gt;", escaped);
        }
    }

    @Nested
    public class EdgeCaseTests {

        @Test
        void throws_npe_when_null_input() {
            assertThrowsExactly(NullPointerException.class, () -> HtmlEscape.escape(null));
        }

        @Test
        void correctly_handles_empty_string() {
            assertEquals("", HtmlEscape.escape(""));
        }

        @Test
        void correctly_handles_whitespace_only() {
            assertEquals("   \t\n", HtmlEscape.escape("   \t\n"));
        }

        @Test
        void correctly_handles_single_space() {
            assertEquals(" ", HtmlEscape.escape(" "));
        }

        @Test
        void correctly_handles_very_long_string() {
            final String longText = "a".repeat(10000) + "<script>";
            final String escaped = HtmlEscape.escape(longText);
            assertEquals("a".repeat(10000) + "&lt;script&gt;", escaped);
        }

        @Test
        void correctly_handles_string_with_only_special_characters() {
            final String input = "&<>\"'&<>\"'";
            final String escaped = HtmlEscape.escape(input);
            assertTrue(escaped.contains("&amp;"));
            assertTrue(escaped.contains("&lt;"));
            assertTrue(escaped.contains("&gt;"));
            assertTrue(escaped.contains("&quot;"));
            assertTrue(escaped.contains("&#39;"));
        }
    }

    @Nested
    public class RealWorldContentTests {

        @Test
        void escapes_html_code_example() {
            final String input = "<!DOCTYPE html>\n<html>\n  <head>\n    <title>Test</title>\n  </head>\n</html>";
            final String escaped = HtmlEscape.escape(input);
            assertFalse(escaped.contains("<!DOCTYPE"));
            assertFalse(escaped.contains("<html>"));
            assertTrue(escaped.contains("&lt;"));
        }

        @Test
        void escapes_javascript_code() {
            final String input = "const obj = { key: \"value\", fn: () => alert('test') };";
            final String escaped = HtmlEscape.escape(input);
            assertEquals("const obj = { key: &quot;value&quot;, fn: () =&gt; alert(&#39;test&#39;) };", escaped);
        }

        @Test
        void escapes_mathematical_expressions() {
            final String input = "5 < 10 && 20 > 15 & x = y";
            final String escaped = HtmlEscape.escape(input);
            assertEquals("5 &lt; 10 &amp;&amp; 20 &gt; 15 &amp; x = y", escaped);
        }

        @Test
        void escapes_user_provided_text_with_quotes() {
            final String input = "The teacher said \"Hello\" and asked 'How are you?'";
            final String escaped = HtmlEscape.escape(input);
            assertEquals("The teacher said &quot;Hello&quot; and asked &#39;How are you?&#39;", escaped);
        }

        @Test
        void escapes_url_with_query_parameters() {
            final String input = "https://example.com/search?q=hello&filter=true&page=1";
            final String escaped = HtmlEscape.escape(input);
            assertEquals("https://example.com/search?q=hello&amp;filter=true&amp;page=1", escaped);
        }
    }

    @Nested
    public class EscapeOrderTests {

        @Test
        void escapes_ampersand_first_preventing_double_escaping() {
            // If & is escaped AFTER <, we'd get &amp;lt; instead of &lt;
            final String input = "<";
            final String escaped = HtmlEscape.escape(input);
            assertEquals("&lt;", escaped);
            // Verify it's not double-escaped
            assertNotEquals("&amp;lt;", escaped);
        }

        @Test
        void already_escaped_entities_are_reescaped() {
            final String input = "&lt;div&gt;";
            final String escaped = HtmlEscape.escape(input);
            // The & should be escaped, resulting in &amp;lt;
            assertEquals("&amp;lt;div&amp;gt;", escaped);
        }

        @Test
        void complex_nesting_escaping_works_correctly() {
            final String input = "&amp;&lt;script&gt;";
            final String escaped = HtmlEscape.escape(input);
            assertEquals("&amp;amp;&amp;lt;script&amp;gt;", escaped);
        }
    }

    @Nested
    public class CommonPatternsTests {

        @Test
        void escapes_html_tag_pattern() {
            assertEquals("&lt;div&gt;", HtmlEscape.escape("<div>"));
            assertEquals("&lt;/div&gt;", HtmlEscape.escape("</div>"));
        }

        @Test
        void escapes_self_closing_tag() {
            assertEquals("&lt;br /&gt;", HtmlEscape.escape("<br />"));
        }

        @Test
        void escapes_tag_with_attributes() {
            final String input = "<a href=\"http://example.com\" class='link'>";
            final String escaped = HtmlEscape.escape(input);
            assertEquals("&lt;a href=&quot;http://example.com&quot; class=&#39;link&#39;&gt;", escaped);
        }

        @Test
        void escapes_xml_declaration() {
            final String input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
            final String escaped = HtmlEscape.escape(input);
            assertEquals("&lt;?xml version=&quot;1.0&quot; encoding=&quot;UTF-8&quot;?&gt;", escaped);
        }
    }

    @Nested
    public class IdempotencyTests {

        @Test
        void escaping_same_string_twice_produces_consistent_results() {
            final String input = "<script>alert('XSS')</script>";
            final String escaped1 = HtmlEscape.escape(input);
            final String escaped2 = HtmlEscape.escape(input);
            assertEquals(escaped1, escaped2);
        }

        @Test
        void plain_text_remains_unchanged_after_escaping() {
            final String input = "Hello World 123";
            final String escaped = HtmlEscape.escape(input);
            final String doubleEscaped = HtmlEscape.escape(escaped);
            assertEquals(escaped, doubleEscaped);
        }

        @Test
        void length_increases_proportionally_with_special_characters() {
            final String plain = "hello";
            final String withSpecial = "<test>";
            
            final String escapedPlain = HtmlEscape.escape(plain);
            final String escapedSpecial = HtmlEscape.escape(withSpecial);
            
            assertEquals(plain.length(), escapedPlain.length()); // No change
            assertTrue(escapedSpecial.length() > withSpecial.length()); // Increased
        }
    }

    @Nested
    public class UnicodeTests {

        @Test
        void preserves_unicode_characters() {
            final String input = "Hello 🌍 世界 مرحبا";
            final String escaped = HtmlEscape.escape(input);
            assertEquals(input, escaped);
        }

        @Test
        void escapes_special_characters_with_unicode_content() {
            final String input = "Hello <世界> & 你好";
            final String escaped = HtmlEscape.escape(input);
            assertEquals("Hello &lt;世界&gt; &amp; 你好", escaped);
        }

        @Test
        @DisplayName("handles emoji correctly")
        void correctly_handles_emoji() {
            final String input = "text <tag /> 🎉";
            final String escaped = HtmlEscape.escape(input);
            assertEquals("text &lt;tag /&gt; 🎉", escaped);
        }
    }
}
