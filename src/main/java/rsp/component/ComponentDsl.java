package rsp.component;

import rsp.stateview.CreateViewFunction;

public class ComponentDsl {
    /**
     * A stateful component.
     * @param initialState the component's initial state
     * @param createViewFunction a function for forming the component's view according to a state value
     * @return a component definition
     */
    public static <S> StatefulComponent<S> component(final S initialState, final CreateViewFunction<S> createViewFunction) {
        return new StatefulComponent<>(initialState, createViewFunction);
    }
}
