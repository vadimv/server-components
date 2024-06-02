package rsp.dom;

import java.util.ArrayList;
import java.util.List;

public final class Text implements Node {

    public final List<String> parts = new ArrayList<>();

    public Text(final String text) {
        parts.add(text);
    }

    public void addPart(final String text) {
        parts.add(text);
    }
}
