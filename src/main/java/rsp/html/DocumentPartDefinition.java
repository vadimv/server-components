package rsp.html;

import rsp.page.PageRenderContext;

/**
 * Represents a building block, a node of the input domain-specific language definitions document.
 * For example a definition of a fragment of HTML or an event or a style or something else.
 */
public interface DocumentPartDefinition {

    /**
     * An implementation of this method determines how its definition node is rendered to a virtual DOM tree.
     * @param renderContext the renderer
     */
    void render(PageRenderContext renderContext);

}
