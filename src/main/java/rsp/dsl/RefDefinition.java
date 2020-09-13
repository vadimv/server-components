package rsp.dsl;

import rsp.EventContext;
import rsp.RenderContext;

import java.util.function.Consumer;

public class RefDefinition<S> extends DocumentPartDefinition {

    public RefDefinition() {
        super(DocumentPartKind.OTHER);
    }

    @Override
    public void accept(RenderContext renderContext) {
        renderContext.addRef(this);
    }

}
