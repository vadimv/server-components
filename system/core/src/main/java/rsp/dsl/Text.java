package rsp.dsl;

import rsp.component.TreeBuilder;

/**
 * A text content definition. A null input is converted to the string "null",
 * mimicking Java's string concatenation behavior.
 * <p>
 * Text is stored unescaped. HTML escaping is applied at the serialization
 * boundary ({@link rsp.dom.HtmlBuilder}) where it is needed. The client-side
 * live-update path uses {@code createTextNode()} which is inherently safe
 * against XSS and must receive unescaped text.
 */
public record Text(String text) implements Definition {

    /**
     * Creates a new text definition
     * @param text a string or null
     */
    public Text(final String text) {
        this.text = text != null ? text : "null";
    }

    @Override
    public void render(final TreeBuilder renderContext) {
        renderContext.addTextNode(text);
    }
}
