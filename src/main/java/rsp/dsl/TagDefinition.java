package rsp.dsl;

import rsp.services.RenderContext;
import rsp.dom.XmlNs;

import java.util.Arrays;
import java.util.stream.Stream;

public class TagDefinition extends DocumentPartDefinition {
    private final String name;
    private final DocumentPartDefinition[] children;

    public TagDefinition(String name, DocumentPartDefinition... children) {
        super(DocumentPartDefinition.DocumentPartKind.OTHER);
        this.name = name;
        this.children = children;
    }

    @Override
    public void accept(RenderContext renderContext) {
        renderContext.openNode(XmlNs.html, name);
        Arrays.stream(children).flatMap(c -> c instanceof SequenceDefinition ? Arrays.stream(((SequenceDefinition) c).items) : Stream.of(c))
                .sorted().forEach(c -> c.accept(renderContext));
        renderContext.closeNode(name);
    }
}
