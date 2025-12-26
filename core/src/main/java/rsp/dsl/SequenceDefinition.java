package rsp.dsl;

import rsp.component.TreeBuilder;

import java.util.Arrays;
import java.util.Objects;

/**
 * A sequence of definitions.
 */
public record SequenceDefinition(Definition[] items) implements Definition {

    public SequenceDefinition {
        Objects.requireNonNull(items);
    }

    @Override
    public void render(final TreeBuilder renderContext) {
        Arrays.stream(items).forEach(item -> item.render(renderContext));
    }
}
