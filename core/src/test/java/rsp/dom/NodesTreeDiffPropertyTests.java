package rsp.dom;

import org.junit.jupiter.api.Test;
import rsp.pbt.Gen;
import rsp.pbt.Property;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        Property.forAll(recursiveTagNodes(), recursiveTagNodes()).check((tree1, tree2) -> {
            final PatchCollectingChangesContext cp = new PatchCollectingChangesContext();
            NodesTreeDiff.diff(tree1, tree2, new TreePositionPath(1), cp, new HtmlBuilder(new StringBuilder()));

            final Patch patch = new Patch(cp.modifications);
            final TagNode tree1afterApply = apply(tree1, patch);

            assertEquals(tree2.toString(), tree1afterApply.toString());
        });
    }

    @Test
    void diff_should_be_reversible() {
        Property.forAll(recursiveTagNodes(), recursiveTagNodes()).check((tree1, tree2) -> {
            // Forward: tree1 -> tree2
            final PatchCollectingChangesContext cp1 = new PatchCollectingChangesContext();
            NodesTreeDiff.diff(tree1, tree2, new TreePositionPath(1), cp1, new HtmlBuilder(new StringBuilder()));
            final TagNode result1 = apply(tree1, new Patch(cp1.modifications));
            assertEquals(tree2.toString(), result1.toString(), "Forward transformation failed");

            // Backward: tree2 -> tree1
            final PatchCollectingChangesContext cp2 = new PatchCollectingChangesContext();
            NodesTreeDiff.diff(tree2, tree1, new TreePositionPath(1), cp2, new HtmlBuilder(new StringBuilder()));
            final TagNode result2 = apply(tree2, new Patch(cp2.modifications));
            assertEquals(tree1.toString(), result2.toString(), "Backward transformation failed");
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

            assertEquals(tree2.toString(), applied.toString(),
                    "keyed diff did not transform " + keys1 + " into " + keys2);
        });
    }

    private Gen<List<Long>> keyedKeyLists() {
        return Gen.longs(1, 9).listUnique(0, 7);
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
        return applySequential(root, patch);
    }

    private TagNode applySequential(final TagNode root, final Patch patch) {
        final TagNode copy = deepCopy(root);
        final MutableDom dom = new MutableDom(copy);

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

        return dom.getRoot();
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
            () -> Gen.alpha(1, 5).map(name -> new TagNode(XmlNs.html, name, false)),
            (child) -> Gen.combine(
                Gen.alpha(1, 5),
                child.list(0, 5),
                Gen.maps(Gen.alpha(1, 5), Gen.alpha(1, 5), 3),
                Gen.alpha(1, 5),
                (name, children, attrs, text) -> {
                    final TagNode node = new TagNode(XmlNs.html, name, false);
                    node.addChild(new TextNode(text));
                    children.forEach(node::addChild);
                    attrs.forEach((k, v) -> node.addAttribute(k, v, true));
                    return node;
                }),
            3
        );
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

        TagNode getRoot() {
            return virtualContainer.isEmpty() ? null : (TagNode) virtualContainer.get(0);
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
