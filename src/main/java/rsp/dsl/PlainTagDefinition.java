package rsp.dsl;

import rsp.dom.XmlNs;
import rsp.page.PageRenderContext;

import java.util.Arrays;

/**
 * A definition of an XML tag.
 */
public final class PlainTagDefinition extends TagDefinition {


    /**
     * Creates a new instance of an XML tag's definition.
     * @param name the tag's name
     * @param children the children definitions, this could be another tags, attributes, events, references etc
     */
    public PlainTagDefinition(XmlNs ns, String name, DocumentPartDefinition... children) {
        super(ns, name, children);
    }

    @Override
    public void accept(PageRenderContext renderContext) {
        renderContext.openNode(ns, name);
        Arrays.stream(children).forEach(c -> c.accept(renderContext));
        renderContext.closeNode(name, false);
    }
}
