package rsp.component;

import rsp.html.SegmentDefinition;
import rsp.routing.Routing;
import rsp.server.http.HttpRequest;
import rsp.server.Path;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Stateful and stateless components definitions domain-specific language functions.
 */
public class ComponentDsl {

    /**
     * Creates a stateful component with an initial state provided.
     * @param initialState the component's initial state
     * @param componentView a function for forming the component's view according to a state value
     * @return a component's definition for the DSL
     */
    public static <S> ComponentDefinition<Path, S> component(final S initialState,
                                                             final ComponentView<S> componentView) {
        Objects.requireNonNull(initialState);
        return new ComponentDefinition<>(Path.class,
                                         __ -> CompletableFuture.completedFuture(initialState),
                                         (__, path) -> path,
                                         componentView);
    }

    public static <S> ComponentDefinition<HttpRequest, S> webComponent(final Routing<HttpRequest, S> initialStateRouting,
                                                                       final ComponentView<S> componentView) {
        return new ComponentDefinition<>(HttpRequest.class,
                                         initialStateRouting.toInitialStateFunction(),
                                         (__, path) ->  path,
                                         componentView);
    }

    public static <S> ComponentDefinition<Path, S> component(final Routing<Path, S> initialStateRouting,
                                                             final BiFunction<S, Path, Path> state2PathFunction,
                                                             final ComponentView<S> componentView) {
        return new ComponentDefinition<>(Path.class,
                                         initialStateRouting.toInitialStateFunction(),
                                         state2PathFunction,
                                         componentView);
    }

    public static <S> ComponentDefinition<HttpRequest, S> webComponent(final Routing<HttpRequest, S> initialStateRouting,
                                                                       final BiFunction<S, Path, Path> state2PathFunction,
                                                                       final ComponentView<S> componentView) {
        return new ComponentDefinition<>(HttpRequest.class,
                                         initialStateRouting.toInitialStateFunction(),
                                         state2PathFunction,
                                         componentView);
    }

    public static <S> SegmentDefinition statelessComponent(final S initialState, final View<S> componentView) {
        return componentView.apply(initialState);
    }
}
