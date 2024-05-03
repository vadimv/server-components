package rsp.dom;

import java.util.List;

public class NodeList {
    private final List<? extends Node> nodes;

    public NodeList(final List<? extends Node> nodes) {

        this.nodes = nodes;
    }

    public List<? extends Node> nodes() {
        return nodes;
    }

    public void appendString(StringBuilder sb) {
        nodes.forEach(node -> node.appendString(sb));
    }
}
