package rsp.dom;

import java.util.Objects;

public class Attribute {
    public final String name;
    public final String value;
    public final boolean isProperty;

    public Attribute(String name, String value, boolean isProperty) {
        this.name = name;
        this.value = value;
        this.isProperty = isProperty;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Attribute attribute = (Attribute) o;
        return Objects.equals(name, attribute.name) &&
                Objects.equals(value, attribute.value) &&
                Objects.equals(isProperty, attribute.isProperty);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value, isProperty);
    }
}
