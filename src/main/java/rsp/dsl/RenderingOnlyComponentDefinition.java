package rsp.dsl;

import rsp.Render;
import rsp.page.PageRenderContext;

public abstract class RenderingOnlyComponentDefinition<S> implements DocumentPartDefinition<S> {

    public abstract Render<S> renderer();

    public abstract S state();

    @Override
    public void accept(PageRenderContext renderContext) {
        renderContext.openComponent();
        renderer().render(state()).accept(renderContext);
        renderContext.closeComponent();
    }

    public static class Default<S> extends RenderingOnlyComponentDefinition<S> {
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
