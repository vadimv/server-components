package rsp.html;

import rsp.page.RenderContext;

/**
 * A definition of an HTML element's inline style.
 */
public final class StyleDefinition implements SegmentDefinition {
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
    public StyleDefinition(final String name, final String value) {
        super();
        this.name = name;
        this.value = value;
    }

    @Override
    public boolean render(final RenderContext renderContext) {
        renderContext.setStyle(name, value);
        return true;
    }
}
