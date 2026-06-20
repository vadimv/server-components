package rsp.dom;

import org.junit.jupiter.api.Test;
import rsp.pbt.Gen;
import rsp.pbt.Property;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property-based tests for {@link NodesTreeDiff}, using the in-house {@link Gen}/{@link Property}
 * harness.
 */
class NodesTreeDiffPropertyTests {

    @Test
    void diff_should_be_empty_for_identical_trees() {
        Property.forAll(recursiveTagNodes()).check(tree -> {
            final PatchCollectingChangesContext cp = new PatchCollectingChangesContext();
            NodesTreeDiff.diff(tree, tree, new TreePositionPath(1), cp, new HtmlBuilder(new StringBuilder()));
            assertEquals(true, cp.isEmpty());
        });
    }

    @Test
    void diff_should_correctly_transform_tree1_to_tree2() {
        Property.forAll(recursiveTagNodes(), recursiveTagNodes())
            // Coverage visibility: prints what the generator actually exercised, so a 0% would flag
            // a branch the inputs never reach.
            .classify("has text child",    (t1, t2) -> containsText(t1) || containsText(t2))
            .classify("has attribute",     (t1, t2) -> containsAttr(t1) || containsAttr(t2))
            .classify("has property attr", (t1, t2) -> containsPropertyAttr(t1) || containsPropertyAttr(t2))
            .check((tree1, tree2) -> {
                final PatchCollectingChangesContext cp = new PatchCollectingChangesContext();
                NodesTreeDiff.diff(tree1, tree2, new TreePositionPath(1), cp, new HtmlBuilder(new StringBuilder()));

                final Patch patch = new Patch(cp.modifications);
                final TagNode tree1afterApply = apply(tree1, patch);

                assertEquivalent(tree2, tree1afterApply);
            });
    }

    @Test
    void diff_should_be_reversible() {
        Property.forAll(recursiveTagNodes(), recursiveTagNodes()).check((tree1, tree2) -> {
            // Forward: tree1 -> tree2
            final PatchCollectingChangesContext cp1 = new PatchCollectingChangesContext();
            NodesTreeDiff.diff(tree1, tree2, new TreePositionPath(1), cp1, new HtmlBuilder(new StringBuilder()));
            final TagNode result1 = apply(tree1, new Patch(cp1.modifications));
            assertEquivalent(tree2, result1, "Forward transformation failed");

            // Backward: tree2 -> tree1
            final PatchCollectingChangesContext cp2 = new PatchCollectingChangesContext();
            NodesTreeDiff.diff(tree2, tree1, new TreePositionPath(1), cp2, new HtmlBuilder(new StringBuilder()));
            final TagNode result2 = apply(tree2, new Patch(cp2.modifications));
            assertEquivalent(tree1, result2, "Backward transformation failed");
        });
    }

    /**
     * Keyed diff correctness invariant: for any two sibling lists of keyed children {@code t1, t2},
     * applying the patch produced by {@code diff(t1, t2)} to a copy of {@code t1} yields a tree that
     * is observationally equal to {@code t2}. The harness generates two random keyed lists from a
     * shared key universe so the property exercises pure appends, prepends, mid-list insertions,
     * removals from either end, arbitrary permutations of retained keys, and full replacement.
     */
    @Test
    void applying_keyed_diff_transforms_source_list_into_target_list_for_any_reorder_insert_or_delete() {
        Property.forAll(keyedKeyLists(), keyedKeyLists()).check((keys1, keys2) -> {
            final TagNode tree1 = keyedUl(keys1);
            final TagNode tree2 = keyedUl(keys2);

            final PatchCollectingChangesContext cp = new PatchCollectingChangesContext();
            NodesTreeDiff.diff(tree1, tree2, new TreePositionPath(1), cp, new HtmlBuilder(new StringBuilder()));
            final TagNode applied = apply(tree1, new Patch(cp.modifications));

            assertEquivalent(tree2, applied,
                    "keyed diff did not transform " + keys1 + " into " + keys2);
        });
    }

    /**
     * Deterministic guard for the tag&rarr;text replacement branch of {@link NodesTreeDiff}: when a
     * text sibling precedes the replaced position, the shared {@link HtmlBuilder} must be reset
     * before building the new text, or stale content leaks into the emitted {@code CreateText}. The
     * property above also covers this, but only probabilistically; this example pins the exact
     * hazard so a regression fails on every run.
     */
    @Test
    void replacing_a_tag_with_text_after_a_text_sibling_does_not_leak_stale_html() {
        final TagNode tree1 = new TagNode(XmlNs.html, "u", false); // <u>a<a></a></u>
        tree1.addChild(new TextNode("a"));
        tree1.addChild(new TagNode(XmlNs.html, "a", false));

        final TagNode tree2 = new TagNode(XmlNs.html, "u", false); // <u>ab</u>
        tree2.addChild(new TextNode("a"));
        tree2.addChild(new TextNode("b"));

        final PatchCollectingChangesContext cp = new PatchCollectingChangesContext();
        NodesTreeDiff.diff(tree1, tree2, new TreePositionPath(1), cp, new HtmlBuilder(new StringBuilder()));
        final TagNode applied = apply(tree1, new Patch(cp.modifications));

        assertEquals(tree2.toString(), applied.toString());
    }

    /**
     * Nested keyed diffing. Both trees are <em>uniformly keyed</em> — every parent's children are a
     * keyed group (possibly empty, which is vacuously all-keyed) — so two independently generated
     * trees are always keying-consistent, the precondition {@code requireAllKeyed} enforces, while
     * still differing in keys, counts, order, content and attributes. This exercises keyed
     * reorder/insert/delete and content-diff of retained keys nested at any depth, which the flat
     * {@code keyedUl} property does not.
     */
    @Test
    void nested_keyed_diff_transforms_tree1_into_tree2() {
        Property.forAll(keyedTagNodes(), keyedTagNodes())
            .classify("roots share name", (t1, t2) -> t1.name.equals(t2.name))
            .classify("has keyed child",  (t1, t2) -> containsKeyed(t1) || containsKeyed(t2))
            .check((tree1, tree2) -> {
                final PatchCollectingChangesContext cp = new PatchCollectingChangesContext();
                NodesTreeDiff.diff(tree1, tree2, new TreePositionPath(1), cp, new HtmlBuilder(new StringBuilder()));
                final TagNode applied = apply(tree1, new Patch(cp.modifications));
                assertEquivalent(tree2, applied);
            });
    }

    @Test
    void nested_keyed_diff_is_reversible() {
        Property.forAll(keyedTagNodes(), keyedTagNodes()).check((tree1, tree2) -> {
            final PatchCollectingChangesContext forward = new PatchCollectingChangesContext();
            NodesTreeDiff.diff(tree1, tree2, new TreePositionPath(1), forward, new HtmlBuilder(new StringBuilder()));
            assertEquivalent(tree2, apply(tree1, new Patch(forward.modifications)), "forward");

            final PatchCollectingChangesContext backward = new PatchCollectingChangesContext();
            NodesTreeDiff.diff(tree2, tree1, new TreePositionPath(1), backward, new HtmlBuilder(new StringBuilder()));
            assertEquivalent(tree1, apply(tree2, new Patch(backward.modifications)), "backward");
        });
    }

    /**
     * The public multi-root {@link NodesTreeDiff#diffChildren} entry — the path {@code ComponentSegment}
     * uses to diff a component's root nodes on every update, and which the single-root {@code diff(...)}
     * never reaches. Keyed root lists exercise top-level keyed reconciliation (insert/remove/reorder/
     * content-change of keyed root nodes) through the public entry, under generative load.
     *
     * <p>An <em>unkeyed</em> multi-root variant is deliberately omitted: it surfaced that the test's
     * {@link MutableDom} oracle is positional (numeric ids are live indices that shift on removal),
     * whereas the real client ({@code rsp.js}) is <em>id-keyed</em> — it addresses nodes by stable
     * path-id ({@code this.els[id]}) and never shifts on removal. The diff's emitted patch is correct
     * for the real client; the positional oracle mis-applies it for "remove a leading node + replace a
     * trailing one". Re-enabling the unkeyed variant requires making the oracle id-keyed like the
     * client. Keyed paths match by key (id-like), so they are unaffected.
     */
    @Test
    void diffChildren_round_trips_keyed_root_lists() {
        Property.forAll(keyedRoots(), keyedRoots())
            .check((roots1, roots2) -> checkDiffChildrenRoundTrip(roots1, roots2));
    }

    /** Diffs roots1→roots2 and back via the public {@code diffChildren}; both directions must round-trip. */
    private void checkDiffChildrenRoundTrip(final List<Node> roots1, final List<Node> roots2) {
        final PatchCollectingChangesContext forward = new PatchCollectingChangesContext();
        NodesTreeDiff.diffChildren(roots1, roots2, new TreePositionPath(1), forward, new HtmlBuilder(new StringBuilder()));
        assertEquivalentRoots(roots2, applyToRoots(roots1, new Patch(forward.modifications)), "forward");

        final PatchCollectingChangesContext backward = new PatchCollectingChangesContext();
        NodesTreeDiff.diffChildren(roots2, roots1, new TreePositionPath(1), backward, new HtmlBuilder(new StringBuilder()));
        assertEquivalentRoots(roots1, applyToRoots(roots2, new Patch(backward.modifications)), "backward");
    }

    /** Uniformly keyed root list: 0–5 keyed element siblings, each a uniformly keyed subtree. */
    private Gen<List<Node>> keyedRoots() {
        return keyedGroup(keyedTagNodes());
    }

    private Gen<List<Long>> keyedKeyLists() {
        return Gen.longs(1, 9).listUnique(0, 7);
    }

    /**
     * A uniformly keyed tree: every parent's children are a keyed group (possibly empty). Tag names
     * come from a small alphabet so two trees frequently share a name and the diff recurses into the
     * keyed children rather than replacing whole subtrees.
     */
    private Gen<TagNode> keyedTagNodes() {
        return Gen.recursive(
            () -> Gen.combine(tagName(), attributes(), (name, attrs) -> tag(name, List.of(), attrs)),
            (child) -> Gen.combine(tagName(), keyedGroup(child), attributes(),
                (name, keyedKids, attrs) -> tag(name, keyedKids, attrs)),
            3);
    }

    private static Gen<String> tagName() {
        return Gen.of("a", "b", "c");
    }

    /** A keyed sibling group: 0–5 element children with unique keys from a small shared universe. */
    private Gen<List<Node>> keyedGroup(final Gen<TagNode> child) {
        return Gen.longs(1, 6).listUnique(0, 5).flatMap(keys ->
            child.list(keys.size(), keys.size()).map(kids -> {
                final List<Node> out = new ArrayList<>(kids.size());
                for (int i = 0; i < kids.size(); i++) {
                    // Deep-copy before setting the key so we never mutate a TagNode instance the
                    // shrink tree may share across variants.
                    final TagNode keyed = deepCopy(kids.get(i));
                    keyed.setKey(rsp.dsl.Key.of(keys.get(i)).segment());
                    out.add(keyed);
                }
                return out;
            }));
    }

    /** A {@code <ul>} of keyed {@code <li>} children; each li's text encodes its key so toString distinguishes them. */
    private static TagNode keyedUl(final List<Long> keys) {
        final TagNode ul = new TagNode(XmlNs.html, "ul", false);
        for (final long k : keys) {
            final TagNode li = new TagNode(XmlNs.html, "li", false);
            li.setKey(rsp.dsl.Key.of(k).segment());
            li.addChild(new TextNode("v" + k));
            ul.addChild(li);
        }
        return ul;
    }

    private TagNode apply(final TagNode root, final Patch patch) {
        final MutableDom dom = new MutableDom(deepCopy(root));
        applyPatch(dom, patch);
        return dom.getRoot();
    }

    /** Applies a {@code diffChildren} patch to a copy of a multi-root node list, returning the result. */
    private List<Node> applyToRoots(final List<Node> roots, final Patch patch) {
        final MutableDom dom = new MutableDom(deepCopyList(roots));
        applyPatch(dom, patch);
        return dom.getRoots();
    }

    private List<Node> deepCopyList(final List<Node> nodes) {
        final List<Node> out = new ArrayList<>(nodes.size());
        for (final Node n : nodes) {
            if (n instanceof final TagNode t) {
                out.add(deepCopy(t));
            } else if (n instanceof final TextNode tx) {
                out.add(new TextNode(textOf(tx)));
            }
        }
        return out;
    }

    private static void applyPatch(final MutableDom dom, final Patch patch) {
        // Separate removals from other modifications
        final List<Modification> removals = new ArrayList<>();
        final List<Modification> others = new ArrayList<>();
        for (final Modification mod : patch.modifications) {
            if (mod instanceof RemoveNode) {
                removals.add(mod);
            } else {
                others.add(mod);
            }
        }

        // Sort removals to apply them from the end of lists to the beginning.
        removals.sort((m1, m2) -> {
            final String[] p1 = ((RemoveNode) m1).path().toString().split("_");
            final String[] p2 = ((RemoveNode) m2).path().toString().split("_");

            int depthCompare = Integer.compare(p2.length, p1.length);
            if (depthCompare != 0) return depthCompare;

            for (int i = 0; i < p1.length; i++) {
                final int partCompare;
                if (p1[i].matches("\\d+") && p2[i].matches("\\d+")) {
                    partCompare = Integer.compare(Integer.parseInt(p2[i]), Integer.parseInt(p1[i]));
                } else {
                    partCompare = p2[i].compareTo(p1[i]);
                }
                if (partCompare != 0) return partCompare;
            }
            return 0;
        });

        for (final Modification mod : removals) {
            mod.apply(dom);
        }

        for (final Modification mod : others) {
            mod.apply(dom);
        }
    }

    private TagNode deepCopy(final TagNode node) {
        final TagNode copy = new TagNode(node.xmlns, node.name, node.isSelfClosing);
        copy.setKey(node.key());
        for (final AttributeNode attr : node.attributes) {
            copy.addAttribute(attr.name(), attr.value(), attr.isProperty());
        }
        for (final Node child : node.children) {
            if (child instanceof final TagNode tagNode) {
                copy.addChild(deepCopy(tagNode));
            } else if (child instanceof final TextNode textNode) {
                final StringBuilder sb = new StringBuilder();
                for (final String part : textNode.parts) {
                    sb.append(part);
                }
                copy.addChild(new TextNode(sb.toString()));
            }
        }
        return copy;
    }

    private Gen<TagNode> recursiveTagNodes() {
        return Gen.recursive(
            // Leaf: a (possibly attributed) element with no children.
            () -> Gen.combine(Gen.alpha(1, 5), attributes(), (name, attrs) -> tag(name, List.of(), attrs)),
            (child) -> {
                // Children are a mix of text and element nodes in arbitrary order (not a forced
                // leading text node), so two trees can pit a tag against a text at the same sibling
                // index — exercising the tag<->text replacement branches, not just tag-vs-tag.
                //
                // Keyed children are deliberately NOT generated here: the diff requires a given
                // parent to be consistently keyed across both trees (requireAllKeyed throws
                // otherwise), which two independently generated trees cannot guarantee. Keyed
                // diffing is covered by the keyed-list property below.
                final Gen<List<Node>> children =
                    Gen.oneOf(Gen.alpha(0, 5).map(TextNode::new), child).list(0, 5);
                return Gen.combine(Gen.alpha(1, 5), children, attributes(),
                    (name, kids, attrs) -> tag(name, kids, attrs));
            },
            3
        );
    }

    /**
     * Attributes whose property-ness is a deterministic function of the name (so two independently
     * generated trees agree on it for a given name, as real attributes do), 0–3 per element.
     * Self-closing is deliberately not varied: {@code createTag} carries no self-closing flag in the
     * incremental change model, so it cannot round-trip through this oracle.
     */
    private static Gen<List<AttributeNode>> attributes() {
        return Gen.combine(Gen.alpha(1, 5), Gen.alpha(0, 5),
                (name, value) -> new AttributeNode(name, value, name.length() % 2 == 0))
            .list(0, 3);
    }

    /** Builds an element with the given children and de-duplicated (by name) attributes. */
    private static TagNode tag(final String name, final List<Node> children, final List<AttributeNode> attrs) {
        final TagNode node = new TagNode(XmlNs.html, name, false);
        children.forEach(node::addChild);
        final Set<String> seenNames = new HashSet<>();
        for (final AttributeNode a : attrs) {
            if (seenNames.add(a.name())) {
                node.addAttribute(a.name(), a.value(), a.isProperty());
            }
        }
        return node;
    }

    // --- coverage predicates (for classify) ---

    private static boolean containsKeyed(final TagNode n) {
        for (final Node c : n.children) {
            if (c instanceof final TagNode t && (t.key() != null || containsKeyed(t))) return true;
        }
        return false;
    }

    private static boolean containsText(final TagNode n) {
        for (final Node c : n.children) {
            if (c instanceof TextNode) return true;
            if (c instanceof final TagNode t && containsText(t)) return true;
        }
        return false;
    }

    private static boolean containsAttr(final TagNode n) {
        if (!n.attributes.isEmpty()) return true;
        for (final Node c : n.children) {
            if (c instanceof final TagNode t && containsAttr(t)) return true;
        }
        return false;
    }

    private static boolean containsPropertyAttr(final TagNode n) {
        for (final AttributeNode a : n.attributes) {
            if (a.isProperty()) return true;
        }
        for (final Node c : n.children) {
            if (c instanceof final TagNode t && containsPropertyAttr(t)) return true;
        }
        return false;
    }

    // --- structural oracle ---

    private static void assertEquivalent(final TagNode expected, final TagNode actual) {
        assertEquivalent(expected, actual, "trees are not equivalent");
    }

    private static void assertEquivalent(final TagNode expected, final TagNode actual, final String message) {
        assertTrue(equivalent(expected, actual), () -> message + System.lineSeparator()
                + "  expected: " + expected + System.lineSeparator() + "  actual:   " + actual);
    }

    /** Structural equivalence of two root-node lists (same as {@link #equivalent} but at list level). */
    private static void assertEquivalentRoots(final List<Node> expected, final List<Node> actual, final String message) {
        assertTrue(equivalentLists(expected, actual), () -> message + System.lineSeparator()
                + "  expected: " + expected + System.lineSeparator() + "  actual:   " + actual);
    }

    private static boolean equivalentLists(final List<Node> a, final List<Node> b) {
        final List<Node> ma = mergeAdjacentText(a);
        final List<Node> mb = mergeAdjacentText(b);
        if (ma.size() != mb.size()) {
            return false;
        }
        for (int i = 0; i < ma.size(); i++) {
            if (!equivalent(ma.get(i), mb.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Structural equivalence used instead of {@code toString()} equality, which is too strict on
     * one axis and matched by the diff on another: attribute <em>order</em> is semantically
     * irrelevant (compared here as a set), while adjacent text nodes render as one (merged here, as
     * {@code toString} does). Tag name, key, attributes and child order are all significant.
     */
    private static boolean equivalent(final Node a, final Node b) {
        if (a instanceof final TextNode ta && b instanceof final TextNode tb) {
            return textOf(ta).equals(textOf(tb));
        }
        if (a instanceof final TagNode ga && b instanceof final TagNode gb) {
            if (!ga.name.equals(gb.name) || !Objects.equals(ga.key(), gb.key())) return false;
            if (!attrSet(ga).equals(attrSet(gb))) return false;
            final List<Node> ca = mergeAdjacentText(ga.children);
            final List<Node> cb = mergeAdjacentText(gb.children);
            if (ca.size() != cb.size()) return false;
            for (int i = 0; i < ca.size(); i++) {
                if (!equivalent(ca.get(i), cb.get(i))) return false;
            }
            return true;
        }
        return false;
    }

    private static Set<String> attrSet(final TagNode n) {
        final Set<String> set = new HashSet<>();
        for (final AttributeNode a : n.attributes) {
            set.add(a.name() + "=" + a.value() + (a.isProperty() ? "#prop" : ""));
        }
        return set;
    }

    private static String textOf(final TextNode t) {
        return String.join("", t.parts);
    }

    /** Coalesces runs of adjacent text nodes into one, mirroring how {@code toString} renders them. */
    private static List<Node> mergeAdjacentText(final List<Node> children) {
        final List<Node> out = new ArrayList<>();
        final StringBuilder run = new StringBuilder();
        for (final Node c : children) {
            if (c instanceof final TextNode t) {
                run.append(textOf(t));
            } else {
                if (run.length() > 0) {
                    out.add(new TextNode(run.toString()));
                    run.setLength(0);
                }
                out.add(c);
            }
        }
        if (run.length() > 0) {
            out.add(new TextNode(run.toString()));
        }
        return out;
    }

    // --- Patch Infrastructure ---

    static class PatchCollectingChangesContext implements DomChangesContext {
        final List<Modification> modifications = new ArrayList<>();

        public boolean isEmpty() {
            return modifications.isEmpty();
        }

        @Override
        public void removeNode(final NodeId parentId, final NodeId id) {
            modifications.add(new RemoveNode(parentId, id));
        }

        @Override
        public void createTag(final NodeId id, final XmlNs xmlNs, final String tag) {
            modifications.add(new CreateTag(id, xmlNs, tag));
        }

        @Override
        public void createText(final NodeId parentPath, final NodeId path, final String text) {
            modifications.add(new CreateText(parentPath, path, text));
        }

        @Override
        public void removeAttr(final NodeId id, final XmlNs xmlNs, final String name, final boolean isProperty) {
            modifications.add(new RemoveAttr(id, name, isProperty));
        }

        @Override
        public void setAttr(final NodeId id, final XmlNs xmlNs, final String name, final String value, final boolean isProperty) {
            modifications.add(new SetAttr(id, name, value, isProperty));
        }

        @Override
        public void insertBefore(final NodeId parentId, final NodeId id, final NodeId beforeId) {
            modifications.add(new InsertBefore(parentId, id, beforeId));
        }
    }

    record Patch(List<Modification> modifications) {}

    interface Modification {
        void apply(MutableDom dom);
    }

    record RemoveNode(NodeId parentPath, NodeId path) implements Modification {
        public void apply(final MutableDom dom) { dom.removeNode(parentPath, path); }
    }
    record CreateTag(NodeId path, XmlNs xmlNs, String tag) implements Modification {
        public void apply(final MutableDom dom) { dom.createTag(path, xmlNs, tag); }
    }
    record CreateText(NodeId parentPath, NodeId path, String text) implements Modification {
        public void apply(final MutableDom dom) { dom.createText(parentPath, path, text); }
    }
    record RemoveAttr(NodeId path, String name, boolean isProperty) implements Modification {
        public void apply(final MutableDom dom) { dom.removeAttr(path, name, isProperty); }
    }
    record SetAttr(NodeId path, String name, String value, boolean isProperty) implements Modification {
        public void apply(final MutableDom dom) { dom.setAttr(path, name, value, isProperty); }
    }
    record InsertBefore(NodeId parentPath, NodeId path, NodeId beforePath) implements Modification {
        public void apply(final MutableDom dom) { dom.insertBefore(parentPath, path, beforePath); }
    }

    /**
     * A small mutable DOM mirror that interprets {@link NodeId} segments the way the client does:
     * a numeric segment is a 1-based child index, a key segment ("kn.."/"ks..") matches a child by
     * its {@link TagNode#key()}. Supports insertBefore so keyed reorders can be applied and verified.
     */
    static class MutableDom {
        private final List<Node> virtualContainer = new ArrayList<>();

        MutableDom(final TagNode root) {
            this.virtualContainer.add(root);
        }

        MutableDom(final List<Node> roots) {
            this.virtualContainer.addAll(roots);
        }

        TagNode getRoot() {
            return virtualContainer.isEmpty() ? null : (TagNode) virtualContainer.get(0);
        }

        List<Node> getRoots() {
            return virtualContainer;
        }

        private List<Node> childrenOf(final Object container) {
            if (container instanceof final TagNode tag) return tag.children;
            @SuppressWarnings("unchecked")
            final List<Node> list = (List<Node>) container;
            return list;
        }

        private int indexOfSegment(final List<Node> children, final String segment) {
            if (segment.matches("\\d+")) {
                return Integer.parseInt(segment) - 1;
            }
            for (int i = 0; i < children.size(); i++) {
                if (children.get(i) instanceof final TagNode t && segment.equals(t.key())) {
                    return i;
                }
            }
            return -1; // key not present
        }

        private Object findNodeOrContainer(final NodeId path) {
            if (path.elementsCount() == 0) return virtualContainer;
            Object current = virtualContainer;
            for (int i = 0; i < path.elementsCount(); i++) {
                final List<Node> children = childrenOf(current);
                final int idx = indexOfSegment(children, segmentAt(path, i));
                if (idx < 0 || idx >= children.size()) {
                    throw new IllegalStateException("Node not found at id: " + path + " (segment " + segmentAt(path, i) + ")");
                }
                current = children.get(idx);
            }
            return current;
        }

        private static String segmentAt(final NodeId id, final int i) {
            final String[] parts = id.toString().split("_");
            return parts[i];
        }

        void removeNode(final NodeId parentPath, final NodeId path) {
            final Object parentObj = findNodeOrContainer(parentPath);
            final List<Node> children = childrenOf(parentObj);
            final int index = indexOfSegment(children, path.lastSegment());
            if (index >= 0 && index < children.size()) {
                children.remove(index);
            }
        }

        void createTag(final NodeId path, final XmlNs xmlNs, final String tag) {
            final Object parentObj = findNodeOrContainer(path.parent());
            final List<Node> children = childrenOf(parentObj);
            final TagNode newTag = new TagNode(xmlNs, tag, false);
            final String last = path.lastSegment();
            if (last.matches("\\d+")) {
                final int index = Integer.parseInt(last) - 1;
                if (index >= children.size()) children.add(newTag);
                else children.add(index, newTag);
            } else {
                newTag.setKey(last); // keyed create: record key, append; insertBefore positions it
                children.add(newTag);
            }
        }

        void createText(final NodeId parentPath, final NodeId path, final String text) {
            final Object parentObj = findNodeOrContainer(parentPath);
            final List<Node> children = childrenOf(parentObj);
            final int index = indexOfSegment(children, path.lastSegment());
            final TextNode newText = new TextNode(text);
            if (index < 0 || index >= children.size()) children.add(newText);
            else children.set(index, newText); // replacement of existing node
        }

        void insertBefore(final NodeId parentPath, final NodeId path, final NodeId beforePath) {
            final Object parentObj = findNodeOrContainer(parentPath);
            final List<Node> children = childrenOf(parentObj);
            final int from = indexOfSegment(children, path.lastSegment());
            if (from < 0) return;
            final Node node = children.remove(from);
            if (beforePath == null) {
                children.add(node);
                return;
            }
            final int before = indexOfSegment(children, beforePath.lastSegment());
            if (before < 0 || before > children.size()) children.add(node);
            else children.add(before, node);
        }

        void removeAttr(final NodeId path, final String name, final boolean isProperty) {
            final TagNode node = (TagNode) findNodeOrContainer(path);
            node.attributes.removeIf(a -> a.name().equals(name) && a.isProperty() == isProperty);
        }

        void setAttr(final NodeId path, final String name, final String value, final boolean isProperty) {
            final TagNode node = (TagNode) findNodeOrContainer(path);
            node.attributes.removeIf(a -> a.name().equals(name) && a.isProperty() == isProperty);
            node.addAttribute(name, value, isProperty);
        }
    }
}
