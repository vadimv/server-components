package rsp.dom;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TextNode implements Node {

    public final List<String> parts = new ArrayList<>();

    public TextNode(final String text) {
        Objects.requireNonNull(text);
        parts.add(text);
    }

    public void addPart(final String text) {
        Objects.requireNonNull(text);
        parts.add(text);
    }
}
