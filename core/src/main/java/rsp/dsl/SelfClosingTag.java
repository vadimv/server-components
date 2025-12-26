package rsp.dsl;

import rsp.component.TreeBuilder;
import rsp.dom.XmlNs;

import java.util.Arrays;

/**
 * A definition of a self-closing XML tag.
 */
public final class SelfClosingTag extends Tag {

    public SelfClosingTag(final XmlNs ns, final String name, final Attribute... attributes) {
        super(ns, name, attributes);
    }

    @Override
    public void render(final TreeBuilder renderContext) {
        renderContext.openNode(ns, name, true);
        Arrays.stream(children).forEach(c -> c.render(renderContext));
        renderContext.closeNode(name, false);
    }
}
