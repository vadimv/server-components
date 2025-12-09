package rsp.dsl;

import rsp.component.TreeBuilder;

import java.util.Arrays;

/**
 * An ordered sequence of definitions.
 */
public final class SequenceDefinition implements Definition {
    public final Definition[] items;

    /**
     * Creates a definition of a sequence of nodes definitions.
     * @param items to be rendered one ofter another
     */
    public SequenceDefinition(final Definition[] items) {
        super();
        this.items = items;
    }

    @Override
    public boolean render(final TreeBuilder renderContext) {
        Arrays.stream(items).forEach(c -> c.render(renderContext));
        return true;
    }
}
