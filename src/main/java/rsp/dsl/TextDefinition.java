package rsp.dsl;

import rsp.services.RenderContext;

public class TextDefinition extends DocumentPartDefinition {
    private final String text;

    public TextDefinition(String text) {
        super(DocumentPartDefinition.DocumentPartKind.OTHER);
        this.text = text;
    }

    @Override
    public void accept(RenderContext renderContext) {
        renderContext.addTextNode(text);
    }
}
