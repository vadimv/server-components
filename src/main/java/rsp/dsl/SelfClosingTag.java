package rsp.dsl;

import rsp.component.TreeBuilder;
import rsp.dom.XmlNs;

import java.util.Arrays;

public class SelfClosingTag implements Definition {

    protected final XmlNs ns;
    protected final String name;
    protected final Attribute[] attributeDefinitions;

    public SelfClosingTag(XmlNs ns, String name, Attribute... attributeDefinitions) {
        this.ns = ns;
        this.name = name;
        this.attributeDefinitions = attributeDefinitions;
    }

    @Override
    public boolean render(final TreeBuilder renderContext) {
        renderContext.openNode(ns, name, true);
        Arrays.stream(attributeDefinitions).forEach(c -> c.render(renderContext));
        renderContext.closeNode(name, false);
        return true;
    }
}
