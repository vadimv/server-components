package rsp.dsl;

import rsp.Rendering;
import rsp.page.PageRenderContext;

import java.util.function.Function;

public class ComponentDefinition<S1, S2> implements DocumentPartDefinition<S1> {

    public final Rendering<S2> component;
    private final S2 state;
    private final Function<S2, S1> f;

    public ComponentDefinition(Rendering<S2> component, S2 state, Function<S2, S1> f) {
        this.component = component;
        this.state = state;
        this.f = f;
    }

    @Override
    public void accept(PageRenderContext renderContext) {
        component.render(state).accept(renderContext);
    }
}
