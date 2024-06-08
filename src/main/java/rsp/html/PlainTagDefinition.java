package rsp.html;

import rsp.component.ComponentRenderContext;
import rsp.dom.XmlNs;

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
    public PlainTagDefinition(final String name, final SegmentDefinition... children) {
        super(name, children);
    }

    @Override
    public boolean render(final ComponentRenderContext renderContext) {
        renderContext.openNode(name);
        Arrays.stream(children).forEach(c -> c.render(renderContext));
        renderContext.closeNode(name, false);
        return true;
    }
}
