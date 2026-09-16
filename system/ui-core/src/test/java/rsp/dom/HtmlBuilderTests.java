package rsp.dom;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HtmlBuilderTests {

    @Nested
    class EscapeTextMode {

        @Test
        void escapes_text_in_regular_elements() {
            TagNode div = new TagNode(XmlNs.html, "div", false);
            div.addChild(new TextNode("It's a <test> & \"more\""));

            String html = buildHtml(div, true);

            assertEquals("<div>It&#39;s a &lt;test&gt; &amp; &quot;more&quot;</div>", html);
        }

        @Test
        void does_not_escape_text_in_script_elements() {
            TagNode script = new TagNode(XmlNs.html, "script", false);
            script.addChild(new TextNode("window['kfg']={sid:'abc'}"));

            String html = buildHtml(script, true);

            assertEquals("<script>window['kfg']={sid:'abc'}</script>", html);
        }

        @Test
        void does_not_escape_text_in_style_elements() {
            TagNode style = new TagNode(XmlNs.html, "style", false);
            style.addChild(new TextNode("div > span { content: '<'; }"));

            String html = buildHtml(style, true);

            assertEquals("<style>div > span { content: '<'; }</style>", html);
        }

        @Test
        void escapes_text_after_leaving_script_element() {
            TagNode head = new TagNode(XmlNs.html, "head", false);

            TagNode script = new TagNode(XmlNs.html, "script", false);
            script.addChild(new TextNode("var x = 'hello'"));
            head.addChild(script);

            TagNode title = new TagNode(XmlNs.html, "title", false);
            title.addChild(new TextNode("It's a page"));
            head.addChild(title);

            String html = buildHtml(head, true);

            assertEquals("<head><script>var x = 'hello'</script><title>It&#39;s a page</title></head>", html);
        }
    }

    @Nested
    class NoEscapeMode {

        @Test
        void does_not_escape_text_when_escapeText_is_false() {
            TagNode div = new TagNode(XmlNs.html, "div", false);
            div.addChild(new TextNode("It's a <test>"));

            String html = buildHtml(div, false);

            assertEquals("<div>It's a <test></div>", html);
        }
    }

    @Nested
    class InnerHtmlProperty {

        @Test
        void skips_innerHTML_property_during_ssr_and_strips_from_tree() {
            TagNode div = new TagNode(XmlNs.html, "div", false);
            div.addAttribute("innerHTML", "<b>hello</b>", true);

            String html = buildHtml(div, true);

            assertEquals("<div></div>", html);
            // innerHTML should be removed from the virtual DOM so the first diff will re-apply it
            assertTrue(div.attributes.isEmpty(),
                    "innerHTML property should be stripped from virtual DOM after SSR");
        }

        @Test
        void keeps_innerHTML_property_in_no_escape_mode() {
            TagNode div = new TagNode(XmlNs.html, "div", false);
            div.addAttribute("innerHTML", "<b>hello</b>", true);

            String html = buildHtml(div, false);

            // In no-escape mode (diff path), innerHTML is skipped in HTML output but NOT stripped
            assertEquals("<div></div>", html);
            assertFalse(div.attributes.isEmpty(),
                    "innerHTML property should be retained in diff mode");
        }

        @Test
        void renders_regular_class_attribute() {
            TagNode div = new TagNode(XmlNs.html, "div", false);
            div.addAttribute("class", "foo", false);

            String html = buildHtml(div, true);

            assertEquals("<div class=\"foo\"></div>", html);
        }

        @Test
        void renders_value_property_as_attribute() {
            TagNode input = new TagNode(XmlNs.html, "input", true);
            input.addAttribute("value", "foo", true);

            String html = buildHtml(input, true);

            assertEquals("<input value=\"foo\" />", html);
        }
    }

    @Nested
    class SsrLiveUpdateParity {

        @Test
        void ssr_escaped_text_and_diff_raw_text_represent_same_content() {
            final String rawText = "if (a < b) x = 'ok' & y = \"done\"";

            TagNode div = new TagNode(XmlNs.html, "div", false);
            div.addChild(new TextNode(rawText));

            final String ssrHtml = buildHtml(div, true);
            final String diffText = buildHtml(div, false);

            // SSR path HTML-escapes for safe embedding in markup
            assertEquals("<div>if (a &lt; b) x = &#39;ok&#39; &amp; y = &quot;done&quot;</div>", ssrHtml);
            // Diff path keeps raw text for createTextNode() which is inherently safe
            assertEquals("<div>if (a < b) x = 'ok' & y = \"done\"</div>", diffText);
        }
    }

    private static String buildHtml(TagNode node, boolean escapeText) {
        StringBuilder sb = new StringBuilder();
        HtmlBuilder hb = new HtmlBuilder(sb, escapeText);
        hb.buildHtml(node);
        return hb.toString();
    }
}
