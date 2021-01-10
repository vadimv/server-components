package rsp.dsl;

import rsp.page.RenderContext;

/**
 * The void definition, without any representation in the result DOM tree.
 */
final class EmptyDefinition extends DocumentPartDefinition {

    public final static EmptyDefinition INSTANCE = new EmptyDefinition();

    public EmptyDefinition() {
        super(DocumentPartKind.OTHER);
    }

    @Override
    public void accept(RenderContext renderContext) {
        // no-op
    }
}
