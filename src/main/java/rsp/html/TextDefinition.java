package rsp.html;

import rsp.component.ComponentRenderContext;

/**
 * A definition of an HTML tag text content.
 */
public final class TextDefinition implements SegmentDefinition {
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
    public boolean render(final ComponentRenderContext renderContext) {
        renderContext.addTextNode(text);
        return true;
    }
}
