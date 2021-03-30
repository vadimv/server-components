package rsp.dsl;

import rsp.page.PageRenderContext;

import java.util.Arrays;

/**
 * A sequence of definitions.
 */
public final class SequenceDefinition<S> implements DocumentPartDefinition<S> {
    public final DocumentPartDefinition<S>[] items;

    /**
     * Creates a definition of a sequence of nodes definitions.
     * @param items to be rendered one ofter another
     */
    public SequenceDefinition(DocumentPartDefinition<S>[] items) {
        super();
        this.items = items;
    }

    @Override
    public void accept(PageRenderContext renderContext) {
        Arrays.stream(items).forEach(c -> c.accept(renderContext));
    }
}
