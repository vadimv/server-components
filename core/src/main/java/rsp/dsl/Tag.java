package rsp.dsl;
import rsp.component.TreeBuilder;
import rsp.dom.XmlNs;

import java.util.Arrays;
import java.util.Objects;

/**
 * A definition of an XML tag.
 */
public class Tag implements Definition {
    protected final XmlNs ns;
    protected final String name;
    protected final Definition[] children;

    /**
     * Creates a new instance of an XML tag's definition.
     * @param name the tag's name
     * @param children the children definitions, this could be another tags, attributes, events, references etc
     */
    public Tag(final XmlNs ns, final String name, final Definition... children) {
        this.ns = Objects.requireNonNull(ns);
        this.name = Objects.requireNonNull(name);
        this.children = Objects.requireNonNull(children);
    }

    @Override
    public void render(final TreeBuilder renderContext) {
        renderContext.openNode(ns, name, false);
        Arrays.stream(children).forEach(c -> c.render(renderContext));
        renderContext.closeNode(name, true);
    }
}
