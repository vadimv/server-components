package rsp.html;

import rsp.page.PageRenderContext;

import java.util.Arrays;

/**
 * A sequence of definitions.
 */
public final class SequenceDefinition extends BaseDocumentPartDefinition {
    public final DocumentPartDefinition[] items;

    /**
     * Creates a definition of a sequence of nodes definitions.
     * @param items to be rendered one ofter another
     */
    public SequenceDefinition(DocumentPartDefinition[] items) {
        super();
        this.items = items;
    }

    @Override
    public void accept(PageRenderContext renderContext) {
        Arrays.stream(items).forEach(c -> c.accept(renderContext));
    }
}
