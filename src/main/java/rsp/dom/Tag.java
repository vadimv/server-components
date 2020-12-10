package rsp.dom;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public final class Tag implements Node {

    public final VirtualDomPath path;
    public final XmlNs xmlns;
    public final String name;

    public final CopyOnWriteArraySet<Attribute> attributes = new CopyOnWriteArraySet<>();
    public final CopyOnWriteArraySet<Style> styles = new CopyOnWriteArraySet<>();
    public final List<Node> children = new ArrayList<>();

    public Tag(VirtualDomPath path, XmlNs xmlns, String name) {
        this.path = path;
        this.xmlns = xmlns;
        this.name = name;
    }

    public void addChild(Node node) {
        children.add(node);
    }

    public void addAttribute(String name, String value, boolean isProperty) {
        attributes.add(new Attribute(name, value, isProperty));
    }

    public void addStyle(String name, String value) {
        styles.add(new Style(name, value));
    }

    @Override
    public VirtualDomPath path() {
        return path;
    }

    public List<Node> children() {
        return children;
    }

    @Override
    public void appendString(StringBuilder sb) {
        sb.append('<');
        sb.append(name);
        if (styles.size() > 0) {
            sb.append(" style=\"");
            for (Style style: styles) {
                sb.append(style.name);
                sb.append(":");
                sb.append(style.value);
                sb.append(";");
            }
            sb.append('"');
        }
        if (attributes.size() > 0) {
            for (Attribute attribute: attributes) {
                sb.append(' ');
                sb.append(attribute.name);
                sb.append('=');
                sb.append('"');
                sb.append(attribute.value);
                sb.append('"');
            }
        }
        sb.append('>');

        if (children.size() > 0) {
            for (Node childNode: children) {
                childNode.appendString(sb);
            }
        }

        sb.append("</");
        sb.append(name);
        sb.append('>');
    }

}
