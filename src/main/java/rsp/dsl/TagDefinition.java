package rsp.dsl;

import rsp.page.RenderContext;
import rsp.dom.XmlNs;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * A definition of a HTML tag.
 */
public final class TagDefinition extends DocumentPartDefinition {
    private final String name;
    private final DocumentPartDefinition[] children;

    /**
     * Creates a new instance of a HTML tag's definition.
     * @param name the tag's name
     * @param children the children definitions, this could be another tags, attributes, events, references etc
     */
    public TagDefinition(String name, DocumentPartDefinition... children) {
        super(DocumentPartDefinition.DocumentPartKind.OTHER);
        this.name = name;
        this.children = children;
    }

    @Override
    public void accept(RenderContext renderContext) {
        renderContext.openNode(XmlNs.html, name);
        Arrays.stream(children).sorted().forEach(c -> c.accept(renderContext));
        renderContext.closeNode(name);
    }
}
