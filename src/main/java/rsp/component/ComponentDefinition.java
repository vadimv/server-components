package rsp.component;

import rsp.html.SegmentDefinition;
import rsp.page.RenderContext;
import rsp.server.Path;
import rsp.stateview.ComponentView;
import rsp.stateview.NewState;
import rsp.util.data.Tuple2;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class ComponentDefinition<T, S> implements SegmentDefinition {
    private static final System.Logger logger = System.getLogger(ComponentDefinition.class.getName());

    private final Class<T> stateFunctionInputClass;
    private final Function<T, CompletableFuture<S>> initialStateFunction;
    private final BiFunction<S, Path, Path> state2pathFunction;
    private final ComponentView<S> componentView;

    public ComponentDefinition(final Class<T> stateFunctionInputClass,
                               final Function<T, CompletableFuture<S>> initialStateFunction,
                               final BiFunction<S, Path, Path> state2pathFunction,
                               final ComponentView<S> componentView) {
        this.stateFunctionInputClass = Objects.requireNonNull(stateFunctionInputClass);
        this.initialStateFunction = Objects.requireNonNull(initialStateFunction);
        this.state2pathFunction = Objects.requireNonNull(state2pathFunction);
        this.componentView = Objects.requireNonNull(componentView);
    }

/*    public ComponentDefinition(final S initialState,
                               final ComponentView<S> componentView) {
        this((Class<T>) Path.class,
             __ -> CompletableFuture.completedFuture(Objects.requireNonNull(initialState)),
             (__, path) -> path, componentView);
    }*/

    @Override
    public void render(final RenderContext renderContext) {
        final Tuple2<S, NewState<S>> newStateHandler = renderContext.openComponent(stateFunctionInputClass,
                                                                                   initialStateFunction,
                                                                                   state2pathFunction,
                                                                                   componentView);

        final SegmentDefinition view = componentView.apply(newStateHandler._1).apply(newStateHandler._2);

        view.render(renderContext);

        renderContext.closeComponent();
    }
}
