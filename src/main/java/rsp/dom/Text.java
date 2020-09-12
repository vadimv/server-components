package rsp.dom;

import java.util.Collections;
import java.util.List;

public class Text implements Node {
    public final Path path;
    public final String text;

    public Text(Path path, String text) {
        this.path = path;
        this.text = text;
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public List<Node> children() {
        return Collections.EMPTY_LIST;
    }

}
