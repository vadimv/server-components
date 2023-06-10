package rsp.html;

import rsp.page.RenderContext;

import java.util.Arrays;

/**
 * A sequence of definitions.
 */
public final class SequenceDefinition extends BaseSegmentDefinition {
    public final SegmentDefinition[] items;

    /**
     * Creates a definition of a sequence of nodes definitions.
     * @param items to be rendered one ofter another
     */
    public SequenceDefinition(final SegmentDefinition[] items) {
        super();
        this.items = items;
    }

    @Override
    public void render(final RenderContext renderContext) {
        Arrays.stream(items).forEach(c -> c.render(renderContext));
    }
}
