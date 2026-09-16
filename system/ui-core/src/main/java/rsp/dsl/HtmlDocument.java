package rsp.dsl;

import rsp.component.TreeBuilder;
import rsp.dom.XmlNs;

/**
 * A definition of an HTML document.
 */
public final class HtmlDocument extends Tag {
    public HtmlDocument(final Definition... children) {
        super(XmlNs.html, "html", children);
    }

    @Override
    public void render(final TreeBuilder renderContext) {
        renderContext.setDocType("<!DOCTYPE html>");
        super.render(renderContext);
    }
}
