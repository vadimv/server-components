package rsp.dom;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public final class Tag implements Node {

    public final XmlNs xmlns;
    public final String name;
    public final boolean isSelfClosing;

    public final CopyOnWriteArraySet<Attribute> attributes = new CopyOnWriteArraySet<>();
    public final CopyOnWriteArraySet<Style> styles = new CopyOnWriteArraySet<>();
    public final List<Node> children = new ArrayList<>();

    public Tag(final XmlNs xmlns, final String name, boolean isSelfClosing) {
        this.xmlns = xmlns;
        this.name = name;
        this.isSelfClosing = isSelfClosing;
    }

    public void addChild(final Node node) {
        children.add(node);
    }

    public void addAttribute(final String name, final String value, final boolean isProperty) {
        attributes.add(new Attribute(name, value, isProperty));
    }

    public void addStyle(final String name, final String value) {
        styles.add(new Style(name, value));
    }

    @Override
    public String toString() {
        final HtmlBuilder htmlBuilder = new HtmlBuilder(new StringBuilder());
        htmlBuilder.buildHtml(this);
        return htmlBuilder.toString();
    }
}
