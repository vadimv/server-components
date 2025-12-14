package rsp.dsl;

import rsp.component.TreeBuilder;
import rsp.util.html.HtmlEscape;

/**
 * A definition of an HTML tag text content.
 * Text content is automatically HTML-escaped to prevent XSS attacks and ensure
 * special characters like {@code <}, {@code >}, {@code &}, and quotes are displayed
 * correctly rather than being interpreted as markup.
 * 
 * @see HtmlEscape for the escaping utility
 */
public final class Text implements Definition {
    private final String text;

    /**
     * Creates a new instance of a text definition.
     * The provided text will be automatically HTML-escaped using {@link HtmlEscape#escape(String)}.
     * @param text a text {@link String} to be HTML-escaped, if it is null then 'null' text to be added
     */
    public Text(final String text) {
        this.text = text != null ? HtmlEscape.escape(text) : "null";
    }

    @Override
    public boolean render(final TreeBuilder renderContext) {
        renderContext.addTextNode(text);
        return true;
    }
}
