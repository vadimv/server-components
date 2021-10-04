package rsp.html;

import rsp.page.PageRenderContext;

/**
 * A definition of a HTML tag text content.
 */
public final class TextDefinition extends BaseDocumentPartDefinition {
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
    public void accept(PageRenderContext renderContext) {
        renderContext.addTextNode(text);
    }
}
