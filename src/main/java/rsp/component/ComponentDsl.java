package rsp.component;

import rsp.html.SegmentDefinition;
import rsp.html.TagDefinition;
import rsp.routing.Routing;
import rsp.server.HttpRequest;
import rsp.server.Path;
import rsp.stateview.ComponentView;
import rsp.stateview.View;

import java.util.function.BiFunction;

public class ComponentDsl {

    /**
     * A stateful component.
     * @param initialState the component's initial state
     * @param componentView a function for forming the component's view according to a state value
     * @return a component definition
     */
    public static <S> SegmentDefinition component(final S initialState, final ComponentView<S> componentView) {
        return new ComponentDefinition<S>(initialState, componentView);
    }

    public static <S> SegmentDefinition component(final Routing<HttpRequest, S> initialStateRouting,
                                                  final ComponentView<S> componentView) {
        return new ComponentDefinition<S>(initialStateRouting.toInitialStateFunction(),
                                          (s, p) ->  p,
                                          componentView);
    }

    public static <S> SegmentDefinition component(final Routing<HttpRequest, S> initialStateRouting,
                                                  final BiFunction<S, Path, Path> state2PathFunction,
                                                  final ComponentView<S> componentView) {
        return new ComponentDefinition<S>(initialStateRouting.toInitialStateFunction(),
                                          state2PathFunction,
                                          componentView);
    }

    public static <S> TagDefinition statelessComponent(final S initialState, final View<S> componentView) {
        return componentView.apply(initialState);
    }
}
