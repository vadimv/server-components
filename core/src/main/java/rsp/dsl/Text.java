package rsp.dsl;

import rsp.component.TreeBuilder;
import rsp.util.html.HtmlEscape;

/**
 * A text content definition. This class ensures that the provided text
 * is always HTML-escaped to prevent XSS vulnerabilities. A null input
 * is converted to the string "null", mimicking Java's string concatenation behavior.
 */
public record Text(String text) implements Definition {

    /**
     * Creates an new text definition
     * @param text a string or null
     */
    public Text(final String text) {
        this.text = text != null ? HtmlEscape.escape(text) : "null";
    }

    @Override
    public void render(final TreeBuilder renderContext) {
        renderContext.addTextNode(text);
    }
}
