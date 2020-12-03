package rsp.dsl;

import rsp.services.RenderContext;

class EmptyDefinition extends DocumentPartDefinition {

    public EmptyDefinition() {
        super(DocumentPartKind.OTHER);
    }

    @Override
    public void accept(RenderContext renderContext) {
        // no-op
    }
}
