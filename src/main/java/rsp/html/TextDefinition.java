package rsp.html;

import rsp.page.RenderContext;

/**
 * A definition of an HTML tag text content.
 */
public final class TextDefinition extends BaseSegmentDefinition {
    private final String text;

    /**
     * Creates a new instance of a text definition.
     * @param text a text {@link String}
     */
    public TextDefinition(final String text) {
        super();
        this.text = text;
    }

    @Override
    public boolean render(final RenderContext renderContext) {
        renderContext.addTextNode(text);
        return true;
    }
}
