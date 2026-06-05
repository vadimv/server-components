package rsp.dom;

import org.junit.jupiter.api.Test;
import rsp.dom.DefaultDomChangesContext.*;
import rsp.dsl.Key;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeyedNodesTreeDiffTests {

    private static TagNode li(final long key, final String text) {
        final TagNode li = new TagNode(XmlNs.html, "li", false);
        li.setKey(Key.of(key).segment());
        li.addChild(new TextNode(text));
        return li;
    }

    private static TagNode keyed(final XmlNs xmlNs, final String name, final long key) {
        final TagNode tag = new TagNode(xmlNs, name, false);
        tag.setKey(Key.of(key).segment());
        return tag;
    }

    private static TagNode unkeyedLi(final String text) {
        final TagNode li = new TagNode(XmlNs.html, "li", false);
        li.addChild(new TextNode(text));
        return li;
    }

    private static TagNode ul(final TagNode... lis) {
        final TagNode ul = new TagNode(XmlNs.html, "ul", false);
        for (final TagNode li : lis) {
            ul.addChild(li);
        }
        return ul;
    }

    private static List<DefaultDomChangesContext.DomChange> diff(final TagNode t1, final TagNode t2) {
        final DefaultDomChangesContext cp = new DefaultDomChangesContext();
        NodesTreeDiff.diff(t1, t2, new TreePositionPath(1), cp, new HtmlBuilder(new StringBuilder()));
        return cp.changes;
    }

    private static boolean mentionsKey(final DomChange change, final long key) {
        final String seg = Key.of(key).segment();
        return switch (change) {
            case Create c -> c.path().toString().contains(seg);
            case CreateText c -> c.path().toString().contains(seg) || c.parentPath().toString().contains(seg);
            case Remove c -> c.path().toString().contains(seg);
            case SetAttr c -> c.path().toString().contains(seg);
            case RemoveAttr c -> c.path().toString().contains(seg);
            case InsertBefore c -> c.path().toString().contains(seg);
            default -> false;
        };
    }

    // --- Layer 2: minimality ---

    @Test
    void append_and_drop_touches_only_the_endpoints() {
        final TagNode t1 = ul(li(1, "a"), li(2, "b"), li(3, "c"), li(4, "d"), li(5, "e"));
        final TagNode t2 = ul(li(2, "b"), li(3, "c"), li(4, "d"), li(5, "e"), li(6, "f"));

        final List<DomChange> changes = diff(t1, t2);

        // Retained middle keys 2..5 must not be touched at all.
        for (long k = 2; k <= 5; k++) {
            final long key = k;
            assertTrue(changes.stream().noneMatch(c -> mentionsKey(c, key)),
                    "key " + key + " should not be touched; changes=" + changes);
        }
        // Exactly one removal (key 1) and one created tag (key 6).
        assertEquals(1, changes.stream().filter(c -> c instanceof Remove).count());
        assertEquals(1, changes.stream().filter(c -> c instanceof Create).count());
        assertTrue(changes.stream().anyMatch(c -> c instanceof Remove r && mentionsKey(r, 1)));
        assertTrue(changes.stream().anyMatch(c -> c instanceof Create cr && mentionsKey(cr, 6)));
        // The new node is appended (insertBefore with null reference).
        assertTrue(changes.stream().anyMatch(c -> c instanceof InsertBefore ib && ib.beforePath() == null));
    }

    @Test
    void unchanged_keyed_list_produces_no_changes() {
        final TagNode t1 = ul(li(1, "a"), li(2, "b"), li(3, "c"));
        final TagNode t2 = ul(li(1, "a"), li(2, "b"), li(3, "c"));
        assertTrue(diff(t1, t2).isEmpty());
    }

    @Test
    void content_change_of_one_key_emits_only_that_text() {
        final TagNode t1 = ul(li(1, "a"), li(2, "b"), li(3, "c"));
        final TagNode t2 = ul(li(1, "a"), li(2, "B"), li(3, "c"));

        final List<DomChange> changes = diff(t1, t2);

        assertTrue(changes.stream().noneMatch(c -> mentionsKey(c, 1)));
        assertTrue(changes.stream().noneMatch(c -> mentionsKey(c, 3)));
        assertEquals(1, changes.size());
        assertTrue(changes.get(0) instanceof CreateText ct && mentionsKey(ct, 2));
    }

    @Test
    void same_key_and_name_but_different_namespace_recreates_the_child() {
        final TagNode t1 = ul(keyed(XmlNs.html, "a", 1));
        final TagNode t2 = ul(keyed(XmlNs.svg, "a", 1));

        final List<DomChange> changes = diff(t1, t2);

        assertTrue(changes.stream().anyMatch(c -> c instanceof Remove r && mentionsKey(r, 1)),
                "namespace change must remove the old keyed child; changes=" + changes);
        assertTrue(changes.stream().anyMatch(c -> c instanceof Create cr
                        && mentionsKey(cr, 1)
                        && cr.xmlNs().equals(XmlNs.svg)
                        && cr.tag().equals("a")),
                "namespace change must create the new keyed child with the target namespace; changes=" + changes);
    }

    @Test
    void prepend_creates_and_inserts_before_first() {
        final TagNode t1 = ul(li(1, "a"), li(2, "b"));
        final TagNode t2 = ul(li(0, "z"), li(1, "a"), li(2, "b"));

        final List<DomChange> changes = diff(t1, t2);

        assertTrue(changes.stream().noneMatch(c -> mentionsKey(c, 1)));
        assertTrue(changes.stream().noneMatch(c -> mentionsKey(c, 2)));
        assertEquals(1, changes.stream().filter(c -> c instanceof Create).count());
        // Inserted before the previously-first key (1), not appended.
        assertTrue(changes.stream().anyMatch(c -> c instanceof InsertBefore ib
                && mentionsKey(ib, 0)
                && ib.beforePath() != null
                && ib.beforePath().toString().contains(Key.of(1L).segment())));
    }

    // --- Layer 3: guards ---

    @Test
    void mixed_keyed_and_unkeyed_siblings_throws() {
        final TagNode t1 = ul(li(1, "a"), li(2, "b"));
        final TagNode t2 = ul(li(1, "a"), unkeyedLi("b"));
        assertThrows(IllegalStateException.class, () -> diff(t1, t2));
    }

    @Test
    void duplicate_keys_among_siblings_throws() {
        final TagNode t1 = ul(li(1, "a"));
        final TagNode t2 = ul(li(1, "a"), li(1, "dup"));
        assertThrows(IllegalStateException.class, () -> diff(t1, t2));
    }
}
