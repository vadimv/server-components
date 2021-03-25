package rsp.dsl;

import rsp.page.RenderContext;
import rsp.dom.XmlNs;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * A definition of an XML tag.
 */
public class TagDefinition implements DocumentPartDefinition {
    protected final XmlNs ns;
    protected final String name;
    protected final DocumentPartDefinition[] children;

    /**
     * Creates a new instance of an XML tag's definition.
     * @param name the tag's name
     * @param children the children definitions, this could be another tags, attributes, events, references etc
     */
    public TagDefinition(XmlNs ns, String name, DocumentPartDefinition... children) {
        super();
        this.ns = ns;
        this.name = name;
        this.children = children;
    }

    @Override
    public void accept(RenderContext renderContext) {
        renderContext.openNode(ns, name);
        Arrays.stream(children).forEach(c -> c.accept(renderContext));
        renderContext.closeNode(name, true);
    }
}
