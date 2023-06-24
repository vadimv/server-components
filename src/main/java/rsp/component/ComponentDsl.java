package rsp.component;

import rsp.html.SegmentDefinition;
import rsp.html.TagDefinition;
import rsp.stateview.ComponentView;
import rsp.stateview.View;

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

    public static <S> TagDefinition statelessComponent(final S initialState, final View<S> componentView) {
        return componentView.apply(initialState);
    }
}
