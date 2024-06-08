package rsp.dom;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class Tag implements Node {

    public static Set<String> VOID_ELEMENTS_NAMES = Set.of("area", "base", "br", "col", "command", "embed", "hr", "img", "input", "keygen", "link", "meta", "param", "source", "track", "wbr");

    public final String name;

    public final CopyOnWriteArraySet<Attribute> attributes = new CopyOnWriteArraySet<>();
    public final CopyOnWriteArraySet<Style> styles = new CopyOnWriteArraySet<>();
    public final List<Node> children = new ArrayList<>();

    public Tag(final String name) {
        this.name = name;
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

    public boolean isSelfClosing() {
        return VOID_ELEMENTS_NAMES.contains(name);
    }

    @Override
    public String toString() {
        final HtmlBuilder htmlBuilder = new HtmlBuilder(new StringBuilder());
        htmlBuilder.buildHtml(this);
        return htmlBuilder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tag tag = (Tag) o;
        return Objects.equals(name, tag.name) && Objects.equals(attributes, tag.attributes) && Objects.equals(styles, tag.styles) && Objects.equals(children, tag.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, attributes, styles, children);
    }
}
