package rsp.dsl;

import rsp.component.ComponentRenderContext;

/**
 * A definition of an HTML tag text content.
 */
public final class Text implements Definition {
    private final String text;

    /**
     * Creates a new instance of a text definition.
     * @param text a text {@link String}
     */
    public Text(final String text) {
        super();
        this.text = text;
    }

    @Override
    public boolean render(final ComponentRenderContext renderContext) {
        renderContext.addTextNode(text);
        return true;
    }
}
