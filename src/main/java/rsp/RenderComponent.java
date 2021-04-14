package rsp;

import rsp.dsl.DocumentPartDefinition;

/**
 * A UI component, a building block of a UI.
 * @param <S1> the type of the component's state, should be an immutable class
 */
@FunctionalInterface
public interface RenderComponent<S1, S2> {
    /**
     * Constructs a UI tree, which may contain HTML tags and/or descendant components.
     * In this method the component's state is reflected to its UI presentation and events handlers registered.
     * @param s a read and write state accessor object
     * @return the result component's definition
     */
    DocumentPartDefinition<S1> render(S2 s);
}
