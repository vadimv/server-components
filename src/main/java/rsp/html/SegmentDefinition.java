package rsp.html;

import rsp.page.RenderContext;

/**
 * Represents a building block in the domain-specific language definition.
 * For example a definition of a fragment of HTML, tag, attribute, event, style etc.
 */
public interface SegmentDefinition {

    /**
     * An implementation of this method determines how its definition node is rendered to a virtual DOM tree.
     * @param renderContext the renderer
     * @return true, instead of void, used to make the compiler make more strict checks on the DSL
     */
    boolean render(RenderContext renderContext);

}
