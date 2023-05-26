package rsp.html;

import rsp.dom.DomTreePageRenderContext;
import rsp.page.PageRenderContext;

/**
 * The base class for all DLS definitions classes.
 * Contains an implementation of the {@link Object#toString()} method.
 */
public abstract class BaseDocumentPartDefinition implements DocumentPartDefinition {

    @Override
    public String toString() {
        final PageRenderContext pageRenderContext = new DomTreePageRenderContext(null);
        render(pageRenderContext);
        return pageRenderContext.toString();
    }
}
