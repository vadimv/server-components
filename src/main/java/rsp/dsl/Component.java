package rsp.dsl;

import rsp.Render;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 *
 * @param <S1> parent component's state type
 * @param <S2> component's state type
 */
public interface Component<S1, S2> {

    Render<S2> renderer();

    Function<Consumer<S1>, Consumer<S2>> transformation();

    default DocumentPartDefinition<S1> render(S2 s) {
        return renderContext -> {
            renderContext.openComponent(transformation());
            renderer().render(s).accept(renderContext);
            renderContext.closeComponent();
        };
    }


   class Default<S1, S2> implements Component<S1, S2> {
        private final Render<S2> component;
        private final Function<Consumer<S1>, Consumer<S2>> transformation;

        public Default(Render<S2> component, Function<Consumer<S1>, Consumer<S2>> transformation) {
            this.component = component;
            this.transformation= transformation;
        }

        @Override
        public Render<S2> renderer() {
            return component;
        }

        @Override
        public Function<Consumer<S1>, Consumer<S2>> transformation() {
            return transformation;
        }
    }
}
