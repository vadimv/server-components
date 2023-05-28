package rsp.html;

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
    public PlainTagDefinition(final XmlNs ns, final String name, final DocumentPartDefinition... children) {
        super(ns, name, children);
    }

    @Override
    public void render(final PageRenderContext renderContext) {
        renderContext.openNode(ns, name);
        Arrays.stream(children).forEach(c -> c.render(renderContext));
        renderContext.closeNode(name, false);
    }
}
