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
        final ComponentView<S> componentView = componentView();
        final Component<T, S> newComponent = renderContext.openComponent(stateFunctionInputClass(),
                                                                         initialStateFunction(),
                                                                         state2pathFunction(),
                                                                         componentView);

        final CompletableFuture<? extends S> statePromise = newComponent.resolveState();
        statePromise.whenComplete((state, ex) -> { // TODO handle an exception
            final SegmentDefinition view = componentView.apply(state).apply(newComponent);
            view.render(renderContext);
        });
        renderContext.closeComponent();
        return true;
    }
}
