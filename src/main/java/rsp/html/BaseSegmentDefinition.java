package rsp.html;

import rsp.dom.DomTreeRenderContext;
import rsp.dom.VirtualDomPath;
import rsp.page.RenderContext;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The base class for all DLS definitions classes.
 * Contains an implementation of the {@link Object#toString()} method.
 */
public abstract class BaseSegmentDefinition implements SegmentDefinition {

    @Override
    public String toString() {
        final RenderContext renderContext = new DomTreeRenderContext(VirtualDomPath.DOCUMENT, new AtomicReference<>());
        render(renderContext);
        return renderContext.toString();
    }
}
