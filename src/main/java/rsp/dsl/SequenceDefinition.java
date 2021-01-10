package rsp.dsl;

import rsp.page.RenderContext;

import java.util.Arrays;

public final class SequenceDefinition extends DocumentPartDefinition {
    public final DocumentPartDefinition[] items;

    public SequenceDefinition(DocumentPartDefinition[] items) {
        super(DocumentPartDefinition.DocumentPartKind.OTHER);
        this.items = items;
    }

    @Override
    public void accept(RenderContext renderContext) {
        Arrays.stream(items).sorted().forEach(c -> c.accept(renderContext));
    }
}
