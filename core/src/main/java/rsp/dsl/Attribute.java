package rsp.dsl;

import rsp.component.TreeBuilder;
import rsp.dom.XmlNs;

import java.util.Objects;

/**
 * An attribute's definition.
 */
public record Attribute(String name, String value, boolean isProperty) implements Definition {

    public Attribute {
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);
    }

    @Override
    public void render(final TreeBuilder renderContext) {
        renderContext.setAttr(XmlNs.html, name, value, isProperty);
    }
}
