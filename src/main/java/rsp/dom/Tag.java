package rsp.dom;

import rsp.XmlNs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public class Tag implements Node {

    public final Path path;
    public final XmlNs xmlns;
    public final String name;

    public final CopyOnWriteArraySet<Attribute> attributes = new CopyOnWriteArraySet<>();
    public final CopyOnWriteArraySet<Style> styles = new CopyOnWriteArraySet<>();
    public final List<Node> children = new ArrayList<>();

    public Tag(Path path, XmlNs xmlns, String name) {
        this.path = path;
        this.xmlns = xmlns;
        this.name = name;
    }

    public void addChild(Node node) {
        children.add(node);
    }

    public void addAttribute(String name, String value) {
        attributes.add(new Attribute(name, value));
    }

    public void addStyle(String name, String value) {
        styles.add(new Style(name, value));
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public List<Node> children() {
        return children;
    }

}
