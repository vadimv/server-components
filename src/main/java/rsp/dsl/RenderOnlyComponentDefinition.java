package rsp.dsl;

import rsp.Render;
import rsp.page.PageRenderContext;

public interface RenderOnlyComponentDefinition<S> extends DocumentPartDefinition<S> {

    Render<S> renderer();

    S state();

    @Override
     default void accept(PageRenderContext renderContext) {
        renderContext.openComponent();
        renderer().render(state()).accept(renderContext);
        renderContext.closeComponent();
    }

    class Default<S> implements RenderOnlyComponentDefinition<S> {
        private final Render<S> renderer;
        private final S state;

        public Default(Render<S> renderer, S state) {
            this.renderer = renderer;
            this.state = state;
        }

        @Override
        public Render<S> renderer() {
            return renderer;
        }

        @Override
        public S state() {
            return state;
        }
    }
}
