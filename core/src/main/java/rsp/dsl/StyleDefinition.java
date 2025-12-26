package rsp.dsl;

import rsp.component.TreeBuilder;

import java.util.Objects;

/**
 * A style's definition.
 */
public record StyleDefinition(String name, String value) implements Definition {

    public StyleDefinition {
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);
    }

    @Override
    public void render(final TreeBuilder renderContext) {
        renderContext.setStyle(name, value);
    }
}
