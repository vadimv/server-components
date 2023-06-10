package rsp.html;

import rsp.component.LivePageContext;
import rsp.dom.DomTreeRenderContext;
import rsp.dom.VirtualDomPath;
import rsp.page.RenderContext;

/**
 * The base class for all DLS definitions classes.
 * Contains an implementation of the {@link Object#toString()} method.
 */
public abstract class BaseSegmentDefinition implements SegmentDefinition {

    @Override
    public String toString() {
        final RenderContext renderContext = new DomTreeRenderContext(VirtualDomPath.DOCUMENT, new LivePageContext());
        render(renderContext);
        return renderContext.toString();
    }
}
