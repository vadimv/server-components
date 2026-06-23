package rsp.dom;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the sibling-ordering contract of positional (unkeyed) child diffing.
 *
 * <p>These tests apply the diff's change instructions through a minimal emulator that mirrors the
 * real browser client ({@code js-client/.../rsp.js}) faithfully:
 * <ul>
 *   <li>{@code create}/{@code createText}: if the target wire id still resolves to a node attached
 *       to the same parent, {@code replaceChild} in place; otherwise {@code appendChild} to the end.</li>
 *   <li>{@code remove}: {@code removeChild} only — it does <b>not</b> delete the {@code els} entry, so the
 *       id lingers pointing at a now-detached node (exactly as rsp.js does).</li>
 *   <li>{@code insertBefore}: relocate an existing child before a reference sibling, or append when the
 *       reference is absent.</li>
 * </ul>
 *
 * <p>The consequence: after {@code removeNode(id)}, a following {@code createTag(id)} finds the id
 * detached and therefore <b>appends</b> the replacement to the end of the parent. So replacing a
 * <em>non-final</em> positional child with a different element type relocates it to the end while its
 * surviving siblings stay put — corrupting order. The keyed path guards this with {@code insertBefore}
 * ({@link NodesTreeDiff} line ~180); the positional path must too.
 *
 * <p>The two {@code control_*} tests pass against the current code and exist to prove the emulator is
 * faithful (it is not merely always-failing). The two {@code replacing_*} tests pin the bug.
 */
class NodesTreeDiffPositionalOrderTest {

    // --- the bug: replacing a non-final positional child changes its sibling position ---

    @Test
    void replacing_a_non_final_text_with_an_element_keeps_its_position() {
        assertEquals(
                List.of("tag:a", "text:y"),
                applyDiff(List.of(text("x"), text("y")),
                          List.of(tag("a"), text("y"))));
    }

    @Test
    void replacing_a_non_final_element_with_a_different_element_keeps_its_position() {
        assertEquals(
                List.of("tag:b", "tag:c"),
                applyDiff(List.of(tag("a"), tag("c")),
                          List.of(tag("b"), tag("c"))));
    }

    // --- controls: already correct today; validate that the emulator is faithful ---

    @Test
    void control_in_place_text_change_preserves_order() {
        assertEquals(
                List.of("text:z", "text:y"),
                applyDiff(List.of(text("x"), text("y")),
                          List.of(text("z"), text("y"))));
    }

    @Test
    void control_replacing_the_last_child_is_a_plain_append() {
        assertEquals(
                List.of("tag:a", "tag:b"),
                applyDiff(List.of(tag("a"), text("y")),
                          List.of(tag("a"), tag("b"))));
    }

    // --- harness ---

    /** Renders {@code before}, diffs it against {@code after}, applies the changes, returns the flat order. */
    private static List<String> applyDiff(final List<Node> before, final List<Node> after) {
        final Client client = new Client();
        client.seed(before);
        NodesTreeDiff.diffChildren(before, after, new TreePositionPath(1), client,
                                   new HtmlBuilder(new StringBuilder()));
        return client.order();
    }

    private static TextNode text(final String s) {
        return new TextNode(s);
    }

    private static TagNode tag(final String name) {
        return new TagNode(XmlNs.html, name, false);
    }

    /** A node in the emulated client DOM: a tag or a text node, with a parent and ordered children. */
    private static final class N {
        final boolean isText;
        final String label;
        N parent;
        final List<N> children = new ArrayList<>();

        N(final boolean isText, final String label) {
            this.isText = isText;
            this.label = label;
        }

        String sig() {
            return (isText ? "text:" : "tag:") + label;
        }
    }

    /** Faithful re-implementation of the rsp.js client's id-keyed DOM mutations (flat, single level). */
    private static final class Client implements DomChangesContext {
        private final Map<String, N> els = new HashMap<>();
        private final N root = new N(false, "#root");

        Client() {
            els.put("", root);
        }

        void seed(final List<Node> roots) {
            NodeId id = NodeId.of(new TreePositionPath(1));
            for (final Node node : roots) {
                final N n = toNode(node);
                n.parent = root;
                root.children.add(n);
                els.put(id.toString(), n);
                id = id.incSibling();
            }
        }

        List<String> order() {
            final List<String> out = new ArrayList<>();
            for (final N child : root.children) {
                out.add(child.sig());
            }
            return out;
        }

        @Override
        public void createTag(final NodeId id, final XmlNs xmlNs, final String tag) {
            place(els.get(id.parent().toString()), els.get(id.toString()), new N(false, tag), id);
        }

        @Override
        public void createText(final NodeId parentPath, final NodeId path, final String text) {
            place(els.get(parentPath.toString()), els.get(path.toString()), new N(true, text), path);
        }

        /** replaceChild when the id is still attached to the same parent, else appendChild — as rsp.js does. */
        private void place(final N parent, final N existing, final N created, final NodeId id) {
            if (existing != null && existing.parent == parent) {
                parent.children.set(parent.children.indexOf(existing), created);
                existing.parent = null;
            } else {
                parent.children.add(created);
            }
            created.parent = parent;
            els.put(id.toString(), created);
        }

        @Override
        public void removeNode(final NodeId parentPath, final NodeId path) {
            final N parent = els.get(parentPath.toString());
            final N child = els.get(path.toString());
            if (child != null && child.parent == parent) {
                parent.children.remove(child);
                child.parent = null; // detached; els[path] intentionally left dangling, as rsp.js does
            }
        }

        @Override
        public void insertBefore(final NodeId parentPath, final NodeId path, final NodeId beforePath) {
            final N parent = els.get(parentPath.toString());
            final N child = els.get(path.toString());
            if (parent == null || child == null) {
                return;
            }
            if (child.parent != null) {
                child.parent.children.remove(child);
            }
            final N before = beforePath != null ? els.get(beforePath.toString()) : null;
            if (before != null && before.parent == parent) {
                parent.children.add(parent.children.indexOf(before), child);
            } else {
                parent.children.add(child);
            }
            child.parent = parent;
        }

        @Override
        public void setAttr(final NodeId path, final XmlNs xmlNs, final String name, final String value, final boolean isProperty) {
        }

        @Override
        public void removeAttr(final NodeId path, final XmlNs xmlNs, final String name, final boolean isProperty) {
        }

        private static N toNode(final Node node) {
            if (node instanceof TextNode t) {
                return new N(true, String.join("", t.parts));
            }
            return new N(false, ((TagNode) node).name);
        }
    }
}
