package rsp.dsl;

import rsp.page.RenderContext;

/**
 * The void definition, without any representation in the result DOM tree.
 */
final class EmptyDefinition extends DocumentPartDefinition {
    /**
     * The default instance for reuse.
     */
    public final static EmptyDefinition INSTANCE = new EmptyDefinition();

    /**
     * Creates a new instance of an empty definition.
     */
    public EmptyDefinition() {
        super(DocumentPartKind.OTHER);
    }

    @Override
    public void accept(RenderContext renderContext) {
        // no-op
    }
}
