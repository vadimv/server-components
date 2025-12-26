package rsp.dom;

import java.util.Objects;

public record Style(String name, String value) {
    public Style {
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);
    }
}
