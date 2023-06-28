package rsp.component;

import rsp.html.SegmentDefinition;
import rsp.html.TagDefinition;
import rsp.routing.Routing;
import rsp.server.HttpRequest;
import rsp.server.Path;
import rsp.stateview.ComponentView;
import rsp.stateview.View;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class ComponentDsl {

    /**
     * A stateful component.
     * @param initialState the component's initial state
     * @param componentView a function for forming the component's view according to a state value
     * @return a component definition
     */
    public static <S> SegmentDefinition component(final S initialState,
                                                  final ComponentView<S> componentView) {
        Objects.requireNonNull(initialState);
        return new ComponentDefinition<Path, S>(Path.class,
                                                __ -> CompletableFuture.completedFuture(initialState),
                                                (__, path) -> path,
                                                componentView);
    }

    public static <S> SegmentDefinition webComponent(final Routing<HttpRequest, S> initialStateRouting,
                                                     final ComponentView<S> componentView) {
        return new ComponentDefinition<HttpRequest, S>(HttpRequest.class,
                                                       initialStateRouting.toInitialStateFunction(),
                                                       (__, path) ->  path,
                                                       componentView);
    }

    public static <S> SegmentDefinition component(final Routing<Path, S> initialStateRouting,
                                                     final BiFunction<S, Path, Path> state2PathFunction,
                                                     final ComponentView<S> componentView) {
        return new ComponentDefinition<Path, S>(Path.class,
                                                initialStateRouting.toInitialStateFunction(),
                                                state2PathFunction,
                                                componentView);
    }

    public static <S> SegmentDefinition webComponent(final Routing<HttpRequest, S> initialStateRouting,
                                                     final BiFunction<S, Path, Path> state2PathFunction,
                                                     final ComponentView<S> componentView) {
        return new ComponentDefinition<HttpRequest, S>(HttpRequest.class,
                                                       initialStateRouting.toInitialStateFunction(),
                                                       state2PathFunction,
                                                       componentView);
    }

    public static <S> TagDefinition statelessComponent(final S initialState, final View<S> componentView) {
        return componentView.apply(initialState);
    }
}
