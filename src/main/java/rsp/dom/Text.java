package rsp.dom;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Text implements Node {

    public final List<String> parts = new ArrayList<>();

    public Text(final String text) {
        parts.add(text);
    }

    public void addPart(final String text) {
        parts.add(text);
    }

    @Override
    public String toString() {
        if (parts.size() == 1) {
            return parts.get(0);
        } else if (parts.isEmpty()) {
            return "";
        } else {
            final StringBuilder sb = new StringBuilder();
            parts.forEach(str -> sb.append(str));
            return sb.toString();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o instanceof Text) {
            return toString().equals(o.toString());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
