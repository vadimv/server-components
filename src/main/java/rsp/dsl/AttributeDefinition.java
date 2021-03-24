package rsp.dsl;

import rsp.page.RenderContext;
import rsp.dom.XmlNs;

/**
 * A definition of a HTML element's attribute.
 */
public final class AttributeDefinition extends DocumentPartDefinition {
    /**
     * The attribute's name.
     */
    public final String name;

    /**
     * The attribute's value.
     */
    public final String value;

    /**
     * Determines if this attribute is an HTML tag's property.
     * @see Html#DEFAULT_PROPERTIES_NAMES
     */
    public final boolean isProperty;

    /**
     * Creates a new instance of an attribute definition.
     * @param name the attribute's name
     * @param value the attribute's value
     * @param isProperty true if this attribute is an HTML property and false otherwise
     */
    public AttributeDefinition(String name, String value, boolean isProperty) {
        super();
        this.name = name;
        this.value = value;
        this.isProperty = isProperty;
    }

    @Override
    public void accept(RenderContext renderContext) {
        renderContext.setAttr(XmlNs.html, name, value, isProperty);
    }
}
