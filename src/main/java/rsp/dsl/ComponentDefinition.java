package rsp.dsl;

import rsp.Render;
import rsp.page.PageRenderContext;

import java.util.function.Function;

public class ComponentDefinition<S1, S2> implements DocumentPartDefinition<S1> {

    public final Render<S2> component;
    private final S2 state;
    private final Function<Object, Object> f;

    public ComponentDefinition(Render<S2> component, S2 state, Function<Object, Object> f) {
        this.component = component;
        this.state = state;
        this.f = f;
    }

    @Override
    public void accept(PageRenderContext renderContext) {
        renderContext.openComponent(f);
        component.render(state).accept(renderContext);
        renderContext.closeComponent();
    }
}
