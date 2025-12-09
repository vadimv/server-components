package rsp.dsl;


import rsp.component.TreeBuilder;

/**
 * Represents a building block in the domain-specific language definition.
 * For example a definition of a fragment of HTML, tag, attribute, event, style etc.
 * May contain conditional rendering and event handlers.
 */
public interface Definition {

    /**
     * An implementation of this method determines how this definition is rendered to a virtual DOM tree.
     * @param renderContext the renderer object
     * @return true, instead of void, used to make the compiler make more strict checks on the DSL
     */
    boolean render(TreeBuilder renderContext);

}
