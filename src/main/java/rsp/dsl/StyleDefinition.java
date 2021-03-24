package rsp.dsl;

import rsp.page.RenderContext;

/**
 * A definition of a HTML element's inline style.
 */
public final class StyleDefinition extends DocumentPartDefinition {
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
    public void accept(RenderContext renderContext) {
        renderContext.setStyle(name, value);
    }
}
