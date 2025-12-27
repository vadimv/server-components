package rsp.dom;

import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodesTreeDiffPropertyTests {

    @Property
    void diff_should_be_empty_for_identical_trees(@ForAll("recursiveTagNodes") final TagNode tree) {
        final TestChangesContext cp = new TestChangesContext();
        NodesTreeDiff.diff(tree, tree, new TreePositionPath(1), cp, new HtmlBuilder(new StringBuilder()));
        assertEquals("", cp.resultAsString());
    }

    @Property
    void diff_should_correctly_transform_tree1_to_tree2(@ForAll("recursiveTagNodes") final TagNode tree1,
                                                        @ForAll("recursiveTagNodes") final TagNode tree2) {
        final PatchCollectingChangesContext cp = new PatchCollectingChangesContext();
        NodesTreeDiff.diff(tree1, tree2, new TreePositionPath(1), cp, new HtmlBuilder(new StringBuilder()));
        
        final Patch patch = new Patch(cp.modifications);
        final TagNode tree1afterApply = apply(tree1, patch);
        
        assertEquals(tree2.toString(), tree1afterApply.toString());
    }

    @Property
    void diff_should_be_reversible(@ForAll("recursiveTagNodes") final TagNode tree1,
                                   @ForAll("recursiveTagNodes") final TagNode tree2) {
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

        // Sort removals to apply them from the end of lists to the beginning
        // This prevents index shifting issues for sibling removals.
        removals.sort((m1, m2) -> {
            final RemoveNode r1 = (RemoveNode) m1;
            final RemoveNode r2 = (RemoveNode) m2;
            final TreePositionPath p1 = r1.path();
            final TreePositionPath p2 = r2.path();

            // Compare by depth first (deeper paths first)
            int depthCompare = Integer.compare(p2.elementsCount(), p1.elementsCount());
            if (depthCompare != 0) return depthCompare;

            // If same depth, compare path components from left to right, descending
            for (int i = 0; i < p1.elementsCount(); i++) {
                int n1 = p1.elementAt(i);
                int n2 = p2.elementAt(i);
                int partCompare = Integer.compare(n2, n1);
                if (partCompare != 0) return partCompare;
            }
            return 0;
        });

        // Apply sorted removals
        for (final Modification mod : removals) {
            mod.apply(dom);
        }

        // Apply all other modifications in their original order
        for (final Modification mod : others) {
            mod.apply(dom);
        }
        
        return dom.getRoot();
    }

    private TagNode deepCopy(final TagNode node) {
        final TagNode copy = new TagNode(node.xmlns, node.name, node.isSelfClosing);
        for (final AttributeNode attr : node.attributes) {
            copy.addAttribute(attr.name(), attr.value(), attr.isProperty());
        }
        for (final Style style : node.styles) {
            copy.addStyle(style.name(), style.value());
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

    @Provide
    Arbitrary<TagNode> recursiveTagNodes() {
        return Arbitraries.recursive(
            () -> Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5).map(name -> new TagNode(XmlNs.html, name, false)),
            (child) -> Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5),
                child.list().ofMinSize(0).ofMaxSize(5), // Increased max children to 5
                Arbitraries.maps(Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5), Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5)).ofMaxSize(3)
            ).as((name, children, attrs) -> {
                final TagNode node = new TagNode(XmlNs.html, name, false);
                children.forEach(node::addChild);
                attrs.forEach((k, v) -> node.addAttribute(k, v, true));
                return node;
            }),
            3
        );
    }

    static final class TestChangesContext implements DomChangesContext {
        final StringBuilder sb = new StringBuilder();

        public String resultAsString() {
            return sb.toString().trim();
        }

        @Override
        public void removeNode(final TreePositionPath parentId, final TreePositionPath id) {
            insertDelimiter(sb);
            sb.append("-NODE:" + parentId + ":" + id);
        }

        @Override
        public void createTag(final TreePositionPath id, final XmlNs xmlNs, final String tag) {
            insertDelimiter(sb);
            sb.append("+TAG:" + id + ":" + tag);
        }

        @Override
        public void removeAttr(final TreePositionPath id, final XmlNs xmlNs, final String name, final boolean isProperty) {
            insertDelimiter(sb);
            sb.append("-ATTR:" + id + ":" + name);
        }

        @Override
        public void setAttr(final TreePositionPath id, final XmlNs xmlNs, final String name, final String value, final boolean isProperty) {
            insertDelimiter(sb);
            sb.append("+ATTR:" + id + ":" + name + "=" + value + ":" + isProperty);
        }

        @Override
        public void removeStyle(final TreePositionPath id, final String name) {
            insertDelimiter(sb);
            sb.append("-STYLE:" + id + ":" + name);
        }

        @Override
        public void setStyle(final TreePositionPath id, final String name, final String value) {
            insertDelimiter(sb);
            sb.append("+STYLE:" + id + ":" + name + "=" + value);
        }

        @Override
        public void createText(final TreePositionPath parenPath, final TreePositionPath path, final String text) {
            insertDelimiter(sb);
            sb.append("+TEXT:" + parenPath + ":" + path + "=" + text);
        }

        private void insertDelimiter(final StringBuilder sb) {
            if (sb.length() != 0) sb.append(" ");
        }
    }

    // --- Patch Infrastructure ---

    static class PatchCollectingChangesContext implements DomChangesContext {
        final List<Modification> modifications = new ArrayList<>();

        @Override
        public void removeNode(final TreePositionPath parentId, final TreePositionPath id) {
            modifications.add(new RemoveNode(parentId, id));
        }

        @Override
        public void createTag(final TreePositionPath id, final XmlNs xmlNs, final String tag) {
            modifications.add(new CreateTag(id, xmlNs, tag));
        }

        @Override
        public void createText(final TreePositionPath parentPath, final TreePositionPath path, final String text) {
            modifications.add(new CreateText(parentPath, path, text));
        }

        @Override
        public void removeAttr(final TreePositionPath id, final XmlNs xmlNs, final String name, final boolean isProperty) {
            modifications.add(new RemoveAttr(id, name, isProperty));
        }

        @Override
        public void setAttr(final TreePositionPath id, final XmlNs xmlNs, final String name, final String value, final boolean isProperty) {
            modifications.add(new SetAttr(id, name, value, isProperty));
        }

        @Override
        public void removeStyle(final TreePositionPath id, final String name) {
            modifications.add(new RemoveStyle(id, name));
        }

        @Override
        public void setStyle(final TreePositionPath id, final String name, final String value) {
            modifications.add(new SetStyle(id, name, value));
        }
    }

    record Patch(List<Modification> modifications) {}

    interface Modification {
        void apply(MutableDom dom);
    }

    record RemoveNode(TreePositionPath parentPath, TreePositionPath path) implements Modification {
        public void apply(final MutableDom dom) { dom.removeNode(parentPath, path); }
    }
    record CreateTag(TreePositionPath path, XmlNs xmlNs, String tag) implements Modification {
        public void apply(final MutableDom dom) { dom.createTag(path, xmlNs, tag); }
    }
    record CreateText(TreePositionPath parentPath, TreePositionPath path, String text) implements Modification {
        public void apply(final MutableDom dom) { dom.createText(parentPath, path, text); }
    }
    record RemoveAttr(TreePositionPath path, String name, boolean isProperty) implements Modification {
        public void apply(final MutableDom dom) { dom.removeAttr(path, name, isProperty); }
    }
    record SetAttr(TreePositionPath path, String name, String value, boolean isProperty) implements Modification {
        public void apply(final MutableDom dom) { dom.setAttr(path, name, value, isProperty); }
    }
    record RemoveStyle(TreePositionPath path, String name) implements Modification {
        public void apply(final MutableDom dom) { dom.removeStyle(path, name); }
    }
    record SetStyle(TreePositionPath path, String name, String value) implements Modification {
        public void apply(final MutableDom dom) { dom.setStyle(path, name, value); }
    }

    static class MutableDom {
        private final List<Node> virtualContainer = new ArrayList<>();

        MutableDom(final TagNode root) {
            this.virtualContainer.add(root);
        }
        
        TagNode getRoot() {
            if (virtualContainer.isEmpty()) return null;
            return (TagNode) virtualContainer.get(0);
        }

        private Object findNodeOrContainer(final TreePositionPath path) {
            if (path.elementsCount() == 0) return virtualContainer;
            
            if (path.elementsCount() == 1) {
                final int index = path.elementAt(0) - 1;
                if (index >= 0 && index < virtualContainer.size()) {
                    return virtualContainer.get(index);
                }
                throw new IllegalStateException("Root node not found at path: " + path);
            }

            final int rootIndex = path.elementAt(0) - 1;
            if (rootIndex < 0 || rootIndex >= virtualContainer.size()) {
                 throw new IllegalStateException("Root node not found at path: " + path);
            }
            
            Node current = virtualContainer.get(rootIndex);
            
            for (int i = 1; i < path.elementsCount(); i++) {
                final int childIndex = path.elementAt(i) - 1;
                if (current instanceof final TagNode tagNode) {
                    final List<Node> children = tagNode.children;
                    if (childIndex >= 0 && childIndex < children.size()) {
                        current = children.get(childIndex);
                    } else {
                        throw new IllegalStateException("Node not found at path: " + path + " (index " + childIndex + " out of bounds)");
                    }
                } else {
                    throw new IllegalStateException("Cannot traverse into non-TagNode at path: " + path);
                }
            }
            return current;
        }

        void removeNode(final TreePositionPath parentPath, final TreePositionPath path) {
            final Object parentObj = findNodeOrContainer(parentPath);
            
            final int index = path.elementAt(path.elementsCount() - 1) - 1;

            if (parentObj instanceof final List container) {
                if (index >= 0 && index < container.size()) {
                    container.remove(index);
                }
            } else if (parentObj instanceof final TagNode parent) {
                if (index >= 0 && index < parent.children.size()) {
                    parent.children.remove(index);
                }
            }
        }

        void createTag(final TreePositionPath path, final XmlNs xmlNs, final String tag) {
            final Object parentObj = findNodeOrContainer(path.parent());
            
            final int index = path.elementAt(path.elementsCount() - 1) - 1;
            final TagNode newTag = new TagNode(xmlNs, tag, false);

            if (parentObj instanceof final List container) {
                if (index >= container.size()) {
                    container.add(newTag);
                } else {
                    container.add(index, newTag);
                }
            } else if (parentObj instanceof final TagNode parent) {
                if (index >= parent.children.size()) {
                    parent.children.add(newTag);
                } else {
                    parent.children.add(index, newTag);
                }
            }
        }

        void createText(final TreePositionPath parentPath, final TreePositionPath path, final String text) {
            final Object parentObj = findNodeOrContainer(parentPath);
            final int index = path.elementAt(path.elementsCount() - 1) - 1;
            final TextNode newText = new TextNode(text);

            if (parentObj instanceof final TagNode parent) {
                if (index >= parent.children.size()) {
                    parent.children.add(newText);
                } else {
                    parent.children.add(index, newText);
                }
            } else {
                 throw new IllegalStateException("Cannot add text to virtual container");
            }
        }

        void removeAttr(final TreePositionPath path, final String name, final boolean isProperty) {
            final TagNode node = (TagNode) findNodeOrContainer(path);
            node.attributes.removeIf(a -> a.name().equals(name) && a.isProperty() == isProperty);
        }

        void setAttr(final TreePositionPath path, final String name, final String value, final boolean isProperty) {
            final TagNode node = (TagNode) findNodeOrContainer(path);
            node.attributes.removeIf(a -> a.name().equals(name) && a.isProperty() == isProperty);
            node.addAttribute(name, value, isProperty);
        }

        void removeStyle(final TreePositionPath path, final String name) {
            final TagNode node = (TagNode) findNodeOrContainer(path);
            node.styles.removeIf(s -> s.name().equals(name));
        }

        void setStyle(final TreePositionPath path, final String name, final String value) {
            final TagNode node = (TagNode) findNodeOrContainer(path);
            node.styles.removeIf(s -> s.name().equals(name));
            node.addStyle(name, value);
        }
    }
}
