package rsp.dsl;

import rsp.page.RenderContext;

public final class StyleDefinition extends DocumentPartDefinition {
    public final String name;
    public final String value;

    public StyleDefinition(String name, String value) {
        super(DocumentPartDefinition.DocumentPartKind.STYLE);
        this.name = name;
        this.value = value;
    }

    @Override
    public void accept(RenderContext renderContext) {
        renderContext.setStyle(name, value);
    }
}
