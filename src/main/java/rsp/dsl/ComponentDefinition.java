package rsp.dsl;

import rsp.Render;
import rsp.page.PageRenderContext;

import java.util.function.Function;

/**
 *
 * @param <S1> parent component's state type
 * @param <S2> component's state type
 */
public interface ComponentDefinition<S1, S2> extends DocumentPartDefinition<S1> {

    Render<S2> renderer();

    S2 state();

    Function<S2, S1> transformation();

    @Override
    default void accept(PageRenderContext renderContext) {
        renderContext.openComponent((Function<Object, Object>) transformation());
        renderer().render(state()).accept(renderContext);
        renderContext.closeComponent();
    }


    class Default<S1, S2> implements ComponentDefinition<S1, S2> {
        private final Render<S2> component;
        private final S2 state;
        private final Function<S2, S1> transformation;

        public Default(Render<S2> component, S2 state, Function<S2, S1> transformation) {
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
        public Function<S2, S1> transformation() {
            return transformation;
        }
    }
}
