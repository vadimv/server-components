package rsp.dsl;

import rsp.component.TreeBuilder;
import rsp.dom.XmlNs;

import java.util.Objects;

/**
 * An attribute's definition.
 */
public record Attribute(String name, String value, boolean isProperty) implements Definition {

    /**
     * Creates a new attribute's definition.
     * @param name the attribute's name, must be not null
     * @param value the attribute' value, must not be null
     * @param isProperty a flag defining if this attribute is a property or not
     */
    public Attribute {
        Objects.requireNonNull(name, "Attribute name cannot be null");
        Objects.requireNonNull(value, () -> "Attribute value cannot be null for attribute '" + name + "'");
    }

    @Override
    public void render(final TreeBuilder renderContext) {
        renderContext.setAttr(XmlNs.html, name, value, isProperty);
    }
}
