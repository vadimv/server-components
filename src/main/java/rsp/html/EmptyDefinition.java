package rsp.html;

import rsp.page.RenderContext;

/**
 * The void definition, without any representation in the result DOM tree.
 */
final class EmptyDefinition implements SegmentDefinition {
    /**
     * The default instance for reuse.
     */
    public static final EmptyDefinition INSTANCE = new EmptyDefinition();

    /**
     * Creates a new instance of an empty definition.
     */
    public EmptyDefinition() {
        super();
    }

    @Override
    public boolean render(final RenderContext renderContext) {
        return true;
    }
}
