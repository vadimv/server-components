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
     * <p>The companion {@code diffChildren_round_trips_unkeyed_root_lists} covers positional multi-root
     * reconciliation — including the mid-list node-type replacement that {@link NodesTreeDiff} must
     * reposition with {@code insertBefore} (see {@code NodesTreeDiffPositionalOrderTest}). Both rely on
     * the id-keyed {@link MutableDom} oracle, which mirrors the real client's stable {@code els[id]} map.
     */
    @Test
    void diffChildren_round_trips_keyed_root_lists() {
        Property.forAll(keyedRoots(), keyedRoots())
            .check((roots1, roots2) -> checkDiffChildrenRoundTrip(roots1, roots2));
    }

    @Test
    void diffChildren_round_trips_unkeyed_root_lists() {
        Property.forAll(unkeyedRoots(), unkeyedRoots())
            .check((roots1, roots2) -> checkDiffChildrenRoundTrip(roots1, roots2));
    }

    /** Unkeyed root list: 0–5 siblings, each a text node or an unkeyed element subtree. */
    private Gen<List<Node>> unkeyedRoots() {
        return Gen.oneOf(Gen.alpha(0, 5).map(TextNode::new), recursiveTagNodes()).list(0, 5);
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
        // Apply in emitted order, exactly as the real client (js-client/.../rsp.js) does. The client
        // addresses nodes by a stable wire id, so ops must not be reordered or re-resolved positionally.
        for (final Modification mod : patch.modifications) {
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
     * A mutable DOM mirror faithful to the real browser client ({@code js-client/.../rsp.js}): nodes are
     * held in a stable {@code id -> node} map ({@code els}), so an id keeps addressing the same node even
     * as siblings shift. Mutations match the client exactly:
     * <ul>
     *   <li>{@code create}/{@code createText}: {@code replaceChild} when the id still resolves to a node
     *       under its parent, otherwise {@code appendChild} — they never insert at a positional index.</li>
     *   <li>{@code removeNode}: detaches the node but leaves its {@code els} entry dangling, as the
     *       client's {@code remove} does (so a later create at that id appends rather than replaces).</li>
     *   <li>{@code insertBefore}: relocates an existing child before a reference sibling, or appends.</li>
     * </ul>
     * Ids are seeded over the initial tree exactly as {@link NodesTreeDiff} computes them
     * (positional {@code incLevel}/{@code incSibling}, or keyed {@code child(key)}), under a synthetic
     * root container addressed by the empty id.
     */
    static class MutableDom {
        private final TagNode container = new TagNode(XmlNs.html, "#root", false);
        private final Map<String, Node> els = new HashMap<>();

        MutableDom(final TagNode root) {
            container.addChild(root);
            seedContainer();
        }

        MutableDom(final List<Node> roots) {
            roots.forEach(container::addChild);
            seedContainer();
        }

        TagNode getRoot() {
            return container.children.isEmpty() ? null : (TagNode) container.children.get(0);
        }

        List<Node> getRoots() {
            return new ArrayList<>(container.children);
        }

        private void seedContainer() {
            els.put("", container);
            // Top-level roots are addressed the same way children are: keyed roots by key under the
            // empty id ("".child(key)), unkeyed roots positionally ("1", "2", ...).
            final List<NodeId> ids = childIds(new NodeId(), container.children);
            for (int i = 0; i < container.children.size(); i++) {
                seed(ids.get(i), container.children.get(i));
            }
        }

        private void seed(final NodeId id, final Node node) {
            els.put(id.toString(), node);
            if (node instanceof final TagNode t && !t.children.isEmpty()) {
                final List<NodeId> ids = childIds(id, t.children);
                for (int i = 0; i < t.children.size(); i++) {
                    seed(ids.get(i), t.children.get(i));
                }
            }
        }

        /** Mirrors {@code NodesTreeDiff.childIds}: keyed children by key segment, else positional. */
        private static List<NodeId> childIds(final NodeId parentId, final List<Node> children) {
            boolean keyed = false;
            for (final Node c : children) {
                if (c instanceof final TagNode t && t.key() != null) {
                    keyed = true;
                    break;
                }
            }
            final List<NodeId> ids = new ArrayList<>(children.size());
            if (keyed) {
                for (final Node c : children) {
                    ids.add(parentId.child(((TagNode) c).key()));
                }
            } else {
                NodeId childId = parentId.incLevel();
                for (int i = 0; i < children.size(); i++) {
                    ids.add(childId);
                    if (i < children.size() - 1) {
                        childId = childId.incSibling();
                    }
                }
            }
            return ids;
        }

        void removeNode(final NodeId parentPath, final NodeId path) {
            final Node parent = els.get(parentPath.toString());
            final Node child = els.get(path.toString());
            if (parent instanceof final TagNode p && child != null) {
                p.children.remove(child); // els entry intentionally left dangling, as rsp.js does
            }
        }

        void createTag(final NodeId path, final XmlNs xmlNs, final String tag) {
            final TagNode created = new TagNode(xmlNs, tag, false);
            final String last = path.lastSegment();
            if (!last.matches("\\d+")) {
                created.setKey(last); // keyed segment carries the node's key
            }
            place(path, created);
        }

        void createText(final NodeId parentPath, final NodeId path, final String text) {
            place(path, new TextNode(text));
        }

        /** replaceChild when the id still resolves to a node under its parent, else appendChild. */
        private void place(final NodeId path, final Node created) {
            final TagNode parent = (TagNode) els.get(path.parent().toString());
            final Node existing = els.get(path.toString());
            final int index = existing == null ? -1 : parent.children.indexOf(existing);
            if (index >= 0) {
                parent.children.set(index, created);
            } else {
                parent.children.add(created);
            }
            els.put(path.toString(), created);
        }

        void insertBefore(final NodeId parentPath, final NodeId path, final NodeId beforePath) {
            final TagNode parent = (TagNode) els.get(parentPath.toString());
            final Node child = els.get(path.toString());
            if (parent == null || child == null) {
                return;
            }
            parent.children.remove(child);
            final Node before = beforePath != null ? els.get(beforePath.toString()) : null;
            final int index = before == null ? -1 : parent.children.indexOf(before);
            if (index >= 0) {
                parent.children.add(index, child);
            } else {
                parent.children.add(child);
            }
        }

        void removeAttr(final NodeId path, final String name, final boolean isProperty) {
            final TagNode node = (TagNode) els.get(path.toString());
            node.attributes.removeIf(a -> a.name().equals(name) && a.isProperty() == isProperty);
        }

        void setAttr(final NodeId path, final String name, final String value, final boolean isProperty) {
            final TagNode node = (TagNode) els.get(path.toString());
            node.attributes.removeIf(a -> a.name().equals(name) && a.isProperty() == isProperty);
            node.addAttribute(name, value, isProperty);
        }
    }
}
