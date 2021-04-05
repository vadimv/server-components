package rsp.dsl;

import rsp.Render;
import rsp.page.PageRenderContext;

import java.util.function.Function;

public abstract class ComponentDefinition<S1, S2> implements DocumentPartDefinition<S1> {

    public abstract Render<S2> renderer();

    public abstract S2 state();

    public abstract Function<Object, Object> transformation();

    @Override
    public void accept(PageRenderContext renderContext) {
        renderContext.openComponent(transformation());
        renderer().render(state()).accept(renderContext);
        renderContext.closeComponent();
    }


    public static class Default<S1, S2> extends ComponentDefinition<S1, S2> {
        private final Render<S2> component;
        private final S2 state;
        private final Function<Object, Object> transformation;

        public Default(Render<S2> component, S2 state, Function<Object, Object> transformation) {
            this.component = component;
            this.state = state;
            this.transformation= transformation;
        }

        @Override
        public Render<S2> renderer() {
            return component;
        }

        @Override
        public S2 state() {
            return state;
        }

        @Override
        public Function<Object, Object> transformation() {
            return transformation;
        }
    }
}
