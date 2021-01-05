package rsp;

import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

/**
 * A UI component, a building block of a UI
 * @param <S> the type of the component's state, should be an immutable class
 */
@FunctionalInterface
public interface Component<S> {
    /**
     * Constructs a UI tree, containing base UI elements and/or its descendant components
     * In this method the component's state is reflected to its presentation and events handlers which submit new state registered
     * @param us a read and write state accessor object
     * @return the result component's definition
     */
    DocumentPartDefinition render(UseState<S> us);
}
