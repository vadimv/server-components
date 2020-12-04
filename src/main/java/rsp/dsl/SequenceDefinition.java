package rsp.dsl;

import rsp.services.RenderContext;

final class SequenceDefinition extends DocumentPartDefinition {
    public final DocumentPartDefinition[] items;

    public SequenceDefinition(DocumentPartDefinition[] items) {
        super(DocumentPartDefinition.DocumentPartKind.OTHER);
        this.items = items;
    }

    @Override
    public void accept(RenderContext renderContext) {
        throw new RuntimeException();
    }
}
