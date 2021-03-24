package rsp.dsl;

import rsp.page.RenderContext;

/**
 * A definition of a HTML tag text content.
 */
public final class TextDefinition extends DocumentPartDefinition {
    private final String text;

    /**
     * Creates a new instance of a text definition.
     * @param text a text {@link String}
     */
    public TextDefinition(String text) {
        super();
        this.text = text;
    }

    @Override
    public void accept(RenderContext renderContext) {
        renderContext.addTextNode(text);
    }
}
