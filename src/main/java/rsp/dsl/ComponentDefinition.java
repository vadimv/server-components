package rsp.dsl;

import rsp.Render;
import rsp.page.PageRenderContext;

import java.util.function.Function;

public interface ComponentDefinition<S1, S2> extends DocumentPartDefinition<S1> {

    Render<S2> renderer();

    S2 state();

    Function<Object, Object> transformation();

    @Override
    default void accept(PageRenderContext renderContext) {
        renderContext.openComponent(transformation());
        renderer().render(state()).accept(renderContext);
        renderContext.closeComponent();
    }


    class Default<S1, S2> implements ComponentDefinition<S1, S2> {
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
