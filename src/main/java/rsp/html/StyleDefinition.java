package rsp.html;

import rsp.page.PageRenderContext;

/**
 * A definition of a HTML element's inline style.
 */
public final class StyleDefinition extends BaseDocumentPartDefinition {
    /**
     * The style's name.
     */
    public final String name;

    /**
     * The style's value.
     */
    public final String value;

    /**
     * Creates a new instance of a style definition.
     * @param name the style's name
     * @param value the style's value
     */
    public StyleDefinition(String name, String value) {
        super();
        this.name = name;
        this.value = value;
    }

    @Override
    public void accept(PageRenderContext renderContext) {
        renderContext.setStyle(name, value);
    }
}
