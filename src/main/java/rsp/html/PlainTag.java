package rsp.html;

import rsp.component.ComponentRenderContext;
import rsp.dom.XmlNs;

import java.util.Arrays;

/**
 * A definition of an XML tag.
 */
public final class PlainTag extends Tag {

    /**
     * Creates a new instance of an XML tag's definition.
     * @param name the tag's name
     * @param children the children definitions, this could be another tags, attributes, events, references etc
     */
    public PlainTag(final XmlNs ns, final String name, final SegmentDefinition... children) {
        super(ns, name, children);
    }

    @Override
    public boolean render(final ComponentRenderContext renderContext) {
        renderContext.openNode(ns, name, false);
        Arrays.stream(children).forEach(c -> c.render(renderContext));
        renderContext.closeNode(name, false);
        return true;
    }
}
