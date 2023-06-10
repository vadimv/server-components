package rsp.component;

import rsp.stateview.ComponentView;;

public class ComponentDsl {
    /**
     * A stateful component.
     * @param initialState the component's initial state
     * @param componentView a function for forming the component's view according to a state value
     * @return a component definition
     */
    public static <S> Component<S> component(final S initialState, final ComponentView<S> componentView) {
        return new Component<>(initialState, componentView);
    }
}
