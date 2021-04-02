package rsp.dsl;

import rsp.Rendering;
import rsp.page.PageRenderContext;

import java.util.function.Function;

public class RenderingComponentDefinition<S> implements DocumentPartDefinition<S> {

    public final Rendering<S> component;
    private final S state;

    public RenderingComponentDefinition(Rendering<S> component, S state) {
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
