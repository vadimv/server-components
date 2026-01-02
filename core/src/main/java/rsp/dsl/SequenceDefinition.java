package rsp.dsl;

import rsp.component.TreeBuilder;

import java.util.Arrays;

/**
 * A sequence of definitions.
 */
public record SequenceDefinition(Definition[] items) implements Definition {

    @Override
    public void render(final TreeBuilder renderContext) {
        Arrays.stream(items).forEach(item -> item.render(renderContext));
    }
}
