package rsp.html;


import rsp.component.ComponentRenderContext;

/**
 * Represents a building block in the domain-specific language definition.
 * For example a definition of a fragment of HTML, tag, attribute, event, style etc.
 */
public interface Definition {

    /**
     * An implementation of this method determines how this definition rendered to a virtual DOM tree.
     * @param renderContext the renderer object
     * @return true, instead of void, used to make the compiler make more strict checks on the DSL
     */
    boolean render(ComponentRenderContext renderContext);

}
