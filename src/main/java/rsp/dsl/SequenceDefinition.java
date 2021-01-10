package rsp.dsl;

import rsp.page.RenderContext;

import java.util.Arrays;

/**
 * A sequence of definitions.
 */
public final class SequenceDefinition extends DocumentPartDefinition {
    public final DocumentPartDefinition[] items;

    /**
     * Creates a definition of a sequence of nodes definitions.
     * @param items to be rendered one ofter another
     */
    public SequenceDefinition(DocumentPartDefinition[] items) {
        super(DocumentPartDefinition.DocumentPartKind.OTHER);
        this.items = items;
    }

    @Override
    public void accept(RenderContext renderContext) {
        Arrays.stream(items).sorted().forEach(c -> c.accept(renderContext));
    }
}
