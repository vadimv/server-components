package rsp.dom;

import java.util.ArrayList;
import java.util.List;

public final class TextNode implements Node {

    public final List<String> parts = new ArrayList<>();

    public TextNode(final String text) {
        parts.add(text);
    }

    public void addPart(final String text) {
        parts.add(text);
    }
}
