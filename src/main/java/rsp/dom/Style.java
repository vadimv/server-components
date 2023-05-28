package rsp.dom;

import java.util.Objects;

public final class Style {
    public final String name;
    public final String value;
    public Style(final String name, final String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Style attribute = (Style) o;
        return Objects.equals(name, attribute.name) &&
                Objects.equals(value, attribute.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }
}
