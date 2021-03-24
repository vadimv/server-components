package rsp.dsl;

import rsp.page.RenderContext;

/**
 * Represents a building block, a node of the input domain-specific language definitions document.
 * For example a definition of a fragment of HTML or an event or a style or something else.
 */
public abstract class DocumentPartDefinition {

    /**
     * The base class constructor. Creates a new instance of a definition of a document part.
     */
    public DocumentPartDefinition() {
    }

    /**
     * An implementation of this method determines how its definition node is rendered to a virtual DOM tree.
     * @param renderContext the renderer
     */
    public abstract void accept(RenderContext renderContext);

}
