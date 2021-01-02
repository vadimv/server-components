package rsp.dsl;

import rsp.page.RenderContext;

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
