package rsp.dom;

import java.util.Objects;

public record AttributeNode(String name, String value, boolean isProperty) implements Node {
    public AttributeNode {
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);
    }
}
