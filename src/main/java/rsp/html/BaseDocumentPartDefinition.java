package rsp.html;

import rsp.dom.DomTreePageRenderContext;
import rsp.dom.VirtualDomPath;
import rsp.page.PageRenderContext;

/**
 * The base class for all DLS definitions classes.
 * Contains an implementation of the {@link Object#toString()} method.
 */
public abstract class BaseDocumentPartDefinition implements DocumentPartDefinition {

    @Override
    public String toString() {
        final PageRenderContext pageRenderContext = new DomTreePageRenderContext(VirtualDomPath.DOCUMENT);
        render(pageRenderContext);
        return pageRenderContext.toString();
    }
}
