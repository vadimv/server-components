package rsp.dsl;

import rsp.Render;
import rsp.page.PageRenderContext;

public class RenderingComponentDefinition<S> implements DocumentPartDefinition<S> {

    public final Render<S> component;
    private final S state;

    public RenderingComponentDefinition(Render<S> component, S state) {
        this.component = component;
        this.state = state;
    }

    @Override
    public void accept(PageRenderContext renderContext) {
        renderContext.openComponent();
        component.render(state).accept(renderContext);
        renderContext.closeComponent();
    }
}
