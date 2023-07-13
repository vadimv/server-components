package rsp.html;

import rsp.page.RenderContext;
import rsp.dom.XmlNs;

import java.util.Arrays;
import java.util.Objects;

/**
 * A definition of an XML tag.
 */
public class TagDefinition extends BaseSegmentDefinition {
    protected final XmlNs ns;
    protected final String name;
    protected final SegmentDefinition[] children;

    /**
     * Creates a new instance of an XML tag's definition.
     * @param name the tag's name
     * @param children the children definitions, this could be another tags, attributes, events, references etc
     */
    public TagDefinition(final XmlNs ns, final String name, final SegmentDefinition... children) {
        this.ns = Objects.requireNonNull(ns);
        this.name = Objects.requireNonNull(name);
        this.children = children;
    }

    @Override
    public boolean render(final RenderContext renderContext) {
        renderContext.openNode(ns, name);
        Arrays.stream(children).forEach(c -> c.render(renderContext));
        renderContext.closeNode(name, true);
        return true;
    }
}
