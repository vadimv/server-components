package rsp.dom;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A tag node.
 */
public final class TagNode implements Node {

    public final XmlNs xmlns;
    public final String name;
    public final boolean isSelfClosing;

    public final CopyOnWriteArraySet<AttributeNode> attributes = new CopyOnWriteArraySet<>();
    public final List<Node> children = new ArrayList<>();

    public TagNode(final XmlNs xmlns, final String name, boolean isSelfClosing) {
        this.xmlns = Objects.requireNonNull(xmlns);
        this.name = Objects.requireNonNull(name);
        this.isSelfClosing = isSelfClosing;
    }

    public void addChild(final Node node) {
        Objects.requireNonNull(node);
        children.add(node);
    }

    public void addAttribute(final String name, final String value, final boolean isProperty) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);
        attributes.add(new AttributeNode(name, value, isProperty));
    }

    @Override
    public String toString() {
        final HtmlBuilder htmlBuilder = new HtmlBuilder(new StringBuilder());
        htmlBuilder.buildHtml(this);
        return htmlBuilder.toString();
    }
}
