package rsp.html;

import rsp.page.PageRenderContext;

/**
 * The void definition, without any representation in the result DOM tree.
 */
final class EmptyDefinition implements DocumentPartDefinition {
    /**
     * The default instance for reuse.
     */
    public final static EmptyDefinition INSTANCE = new EmptyDefinition();

    /**
     * Creates a new instance of an empty definition.
     */
    public EmptyDefinition() {
        super();
    }

    @Override
    public void render(PageRenderContext renderContext) {
        // no-op
    }
}
