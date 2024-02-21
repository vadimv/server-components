package rsp.component;

import rsp.html.SegmentDefinition;
import rsp.page.RenderContext;
import rsp.server.Path;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class StatefulComponentDefinition<T, S> implements SegmentDefinition {

    protected abstract Class<T> stateFunctionInputClass();

    protected abstract Function<T, CompletableFuture<? extends S>> initialStateFunction();

    protected abstract BiFunction<S, Path, Path> state2pathFunction();

    protected abstract ComponentView<S> componentView();

    @Override
    public boolean render(final RenderContext renderContext) {
        if (renderContext instanceof ComponentRenderContext componentRenderContext) {
            final ComponentView<S> componentView = componentView();
            final Component<T, S> component = componentRenderContext.openComponent(new Object(),
                                                                                   stateFunctionInputClass(),
                                                                                   initialStateFunction(),
                                                                                   state2pathFunction(),
                                                                                   componentView);
            component.render(renderContext);

            componentRenderContext.closeComponent();
            return true;
        } else {
            return false;
        }
    }
}
