package rsp.html;

import rsp.component.ComponentRenderContext;
import rsp.dom.XmlNs;

import java.util.Arrays;

public class SelfClosingTagDefinition implements SegmentDefinition {

    protected final String name;
    protected final AttributeDefinition[] attributeDefinitions;

    public SelfClosingTagDefinition(String name, AttributeDefinition... attributeDefinitions) {
        this.name = name;
        this.attributeDefinitions = attributeDefinitions;
    }

    @Override
    public boolean render(final ComponentRenderContext renderContext) {
        renderContext.openNode(name, true);
        Arrays.stream(attributeDefinitions).forEach(c -> c.render(renderContext));
        renderContext.closeNode(name, false);
        return true;
    }
}
