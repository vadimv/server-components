package rsp.dom;

import java.util.*;

/**
 * Compares two DOM trees and emits change instructions transforming the first into the second.
 *
 * <p>Children of a tag are diffed in one of two modes:
 * <ul>
 *   <li><b>Positional</b> (default): siblings are matched by index, as before keyed diffing existed.</li>
 *   <li><b>Keyed</b>: when every child of a parent carries a {@link TagNode#key()}, children are matched
 *       by key across renders. Retained children keep their identity and are moved with
 *       {@link DomChangesContext#insertBefore} instead of being rewritten; new keys are created, absent
 *       keys removed.</li>
 * </ul>
 * Mixing keyed and unkeyed siblings under one parent, or duplicate keys among siblings, is rejected.
 *
 * @see DefaultDomChangesContext.DomChange for an atomic transformation
 */
public final class NodesTreeDiff {

    public static void diff(final TagNode tree1,
                            final TagNode tree2,
                            final TreePositionPath tagPath,
                            final DomChangesContext changesPerformer,
                            final HtmlBuilder htmlBuilder) {
        Objects.requireNonNull(tree1);
        Objects.requireNonNull(tree2);
        Objects.requireNonNull(tagPath);
        Objects.requireNonNull(changesPerformer);
        Objects.requireNonNull(htmlBuilder);
        diffNode(tree1, tree2, NodeId.of(tagPath), changesPerformer, htmlBuilder);
    }

    public static void diffChildren(final List<? extends Node> trees1,
                                    final List<? extends Node> trees2,
                                    final TreePositionPath startNodePath,
                                    final DomChangesContext changesPerformer,
                                    final HtmlBuilder htmlBuilder) {
        Objects.requireNonNull(trees1);
        Objects.requireNonNull(trees2);
        Objects.requireNonNull(startNodePath);
        Objects.requireNonNull(changesPerformer);
        Objects.requireNonNull(htmlBuilder);
        diffChildrenById(trees1, trees2, NodeId.of(startNodePath), changesPerformer, htmlBuilder);
    }

    private static void diffNode(final TagNode tree1,
                                 final TagNode tree2,
                                 final NodeId id,
                                 final DomChangesContext changesPerformer,
                                 final HtmlBuilder htmlBuilder) {
        if (!sameElementType(tree1, tree2)) {
            changesPerformer.removeNode(id.parent(), id);
            createNode(tree2, id, changesPerformer, htmlBuilder);
        } else {
            diffAttributes(tree1.attributes, tree2.attributes, id, changesPerformer);
            diffChildrenById(tree1.children, tree2.children, id.incLevel(), changesPerformer, htmlBuilder);
        }
    }

    private static void diffChildrenById(final List<? extends Node> trees1,
                                         final List<? extends Node> trees2,
                                         final NodeId firstChildId,
                                         final DomChangesContext changesPerformer,
                                         final HtmlBuilder htmlBuilder) {
        final boolean keyed1 = hasAnyKey(trees1);
        final boolean keyed2 = hasAnyKey(trees2);
        if (keyed1 || keyed2) {
            requireAllKeyed(trees1);
            requireAllKeyed(trees2);
            diffKeyedChildren(trees1, trees2, firstChildId.parent(), changesPerformer, htmlBuilder);
        } else {
            diffPositionalChildren(trees1, trees2, firstChildId, changesPerformer, htmlBuilder);
        }
    }

    private static void diffPositionalChildren(final List<? extends Node> trees1,
                                               final List<? extends Node> trees2,
                                               final NodeId startNodeId,
                                               final DomChangesContext changesPerformer,
                                               final HtmlBuilder htmlBuilder) {
        final ListIterator<? extends Node> nodesIterator1 = trees1.listIterator();
        final ListIterator<? extends Node> nodesIterator2 = trees2.listIterator();
        NodeId path = startNodeId;
        while (nodesIterator1.hasNext() || nodesIterator2.hasNext()) {
            if (nodesIterator1.hasNext() && nodesIterator2.hasNext()) {
                final Node node1 = nodesIterator1.next();
                final Node node2 = nodesIterator2.next();
                if (node1 instanceof TagNode tagNode1 && node2 instanceof TagNode tagNode2) {
                    diffNode(tagNode1, tagNode2, path, changesPerformer, htmlBuilder);
                } else if (node2 instanceof TagNode t) {
                    changesPerformer.removeNode(path.parent(), path);
                    createNode(t, path, changesPerformer, htmlBuilder);
                } else if (node1 instanceof TagNode) {
                    changesPerformer.removeNode(path.parent(), path);
                    htmlBuilder.reset();
                    htmlBuilder.buildHtml(node2);
                    changesPerformer.createText(path.parent(), path, htmlBuilder.toString());
                } else {
                    htmlBuilder.reset();
                    htmlBuilder.buildHtml(node1);
                    final String ncText = htmlBuilder.toString();
                    htmlBuilder.reset();
                    htmlBuilder.buildHtml(node2);
                    final String nwText = htmlBuilder.toString();
                    if (!ncText.equals(nwText)) {
                        changesPerformer.createText(path.parent(), path, nwText);
                    }
                }
            } else if (nodesIterator1.hasNext()) {
                nodesIterator1.next();
                changesPerformer.removeNode(path.parent(), path);
            } else {
                final Node node2 = nodesIterator2.next();
                if (node2 instanceof TagNode tagNode2) {
                    createNode(tagNode2, path, changesPerformer, htmlBuilder);
                } else {
                    htmlBuilder.reset();
                    htmlBuilder.buildHtml(node2);
                    changesPerformer.createText(path.parent(), path, htmlBuilder.toString());
                }
            }
            if (path.elementsCount() > 0) {
                path = path.incSibling();
            }
        }
    }

    private static void diffKeyedChildren(final List<? extends Node> trees1,
                                          final List<? extends Node> trees2,
                                          final NodeId parentId,
                                          final DomChangesContext changesPerformer,
                                          final HtmlBuilder htmlBuilder) {
        final Map<String, TagNode> oldByKey = indexByKey(trees1);
        final Map<String, TagNode> newByKey = indexByKey(trees2);

        // A key is "retained" only if it is present in both lists with the same element type. A type
        // change cannot be applied in place (diffNode removes and recreates the element), so it is
        // treated as remove+recreate here too — otherwise the recreated element would not be
        // positioned by the insertBefore below and would drift to the end of the parent.

        // 1. Remove keys that are not retained (absent from the new list, or whose tag name changed).
        for (final Node node : trees1) {
            final String key = ((TagNode) node).key();
            if (!isRetained(key, oldByKey, newByKey)) {
                changesPerformer.removeNode(parentId, parentId.child(key));
            }
        }

        // 2. Walk the target order, placing each child. `current` mirrors the live order of retained
        //    children so we only emit a move when a node is not already at its target index.
        final List<String> current = new ArrayList<>();
        for (final Node node : trees1) {
            final String key = ((TagNode) node).key();
            if (isRetained(key, oldByKey, newByKey)) {
                current.add(key);
            }
        }

        for (int ti = 0; ti < trees2.size(); ti++) {
            final String key = ((TagNode) trees2.get(ti)).key();
            final NodeId childId = parentId.child(key);
            final TagNode newNode = newByKey.get(key);

            if (ti < current.size() && current.get(ti).equals(key)) {
                diffNode(oldByKey.get(key), newNode, childId, changesPerformer, htmlBuilder);
                continue;
            }

            final NodeId beforeId = ti < current.size() ? parentId.child(current.get(ti)) : null;
            if (isRetained(key, oldByKey, newByKey)) {
                diffNode(oldByKey.get(key), newNode, childId, changesPerformer, htmlBuilder);
                current.remove(key);
                current.add(ti, key);
            } else {
                createNode(newNode, childId, changesPerformer, htmlBuilder);
                current.add(ti, key);
            }
            changesPerformer.insertBefore(parentId, childId, beforeId);
        }
    }

    /** A keyed child is retained only if it exists in both lists with the same element type. */
    private static boolean isRetained(final String key,
                                      final Map<String, TagNode> oldByKey,
                                      final Map<String, TagNode> newByKey) {
        final TagNode oldNode = oldByKey.get(key);
        final TagNode newNode = newByKey.get(key);
        return oldNode != null && newNode != null && sameElementType(oldNode, newNode);
    }

    private static boolean sameElementType(final TagNode a, final TagNode b) {
        return a.xmlns.equals(b.xmlns) && a.name.equals(b.name);
    }

    private static void diffAttributes(final Set<AttributeNode> attributes1,
                                       final Set<AttributeNode> attributes2,
                                       final NodeId nodeId,
                                       final DomChangesContext changesPerformer) {
        final Set<AttributeNode> attrs1 = new java.util.concurrent.CopyOnWriteArraySet<>(attributes1);
        final Set<AttributeNode> attrs2 = new java.util.concurrent.CopyOnWriteArraySet<>(attributes2);
        attrs1.removeAll(attributes2);
        attrs1.forEach(attribute -> changesPerformer.removeAttr(nodeId, XmlNs.html, attribute.name(), attribute.isProperty()));
        attrs2.removeAll(attributes1);
        attrs2.forEach(attribute -> changesPerformer.setAttr(nodeId, XmlNs.html, attribute.name(), attribute.value(), attribute.isProperty()));
    }

    private static void createNode(final TagNode tag,
                                   final NodeId nodeId,
                                   final DomChangesContext changesPerformer,
                                   final HtmlBuilder htmlBuilder) {
        changesPerformer.createTag(nodeId, tag.xmlns, tag.name);

        for (final AttributeNode attribute : tag.attributes) {
            changesPerformer.setAttr(nodeId, XmlNs.html, attribute.name(), attribute.value(), attribute.isProperty());
        }

        final List<NodeId> childIds = childIds(nodeId, tag.children);
        for (int i = 0; i < tag.children.size(); i++) {
            final Node child = tag.children.get(i);
            final NodeId childId = childIds.get(i);
            if (child instanceof TagNode t) {
                createNode(t, childId, changesPerformer, htmlBuilder);
            } else if (child instanceof TextNode) {
                htmlBuilder.reset();
                htmlBuilder.buildHtml(child);
                changesPerformer.createText(nodeId, childId, htmlBuilder.toString());
            }
        }
    }

    /** Computes the ids for a node's children, keyed or positional, validating consistency. */
    private static List<NodeId> childIds(final NodeId parentId, final List<? extends Node> children) {
        if (hasAnyKey(children)) {
            requireAllKeyed(children);
            indexByKey(children); // validates duplicates
            final List<NodeId> ids = new ArrayList<>(children.size());
            for (final Node child : children) {
                ids.add(parentId.child(((TagNode) child).key()));
            }
            return ids;
        }
        final List<NodeId> ids = new ArrayList<>(children.size());
        NodeId childId = parentId.incLevel();
        for (int i = 0; i < children.size(); i++) {
            ids.add(childId);
            if (i < children.size() - 1) {
                childId = childId.incSibling();
            }
        }
        return ids;
    }

    private static Map<String, TagNode> indexByKey(final List<? extends Node> children) {
        final Map<String, TagNode> byKey = new LinkedHashMap<>();
        for (final Node node : children) {
            final TagNode tag = (TagNode) node;
            final String key = tag.key();
            if (byKey.put(key, tag) != null) {
                throw new IllegalStateException("Duplicate key among keyed siblings: " + key);
            }
        }
        return byKey;
    }

    private static boolean hasAnyKey(final List<? extends Node> children) {
        for (final Node node : children) {
            if (node instanceof TagNode t && t.key() != null) {
                return true;
            }
        }
        return false;
    }

    private static void requireAllKeyed(final List<? extends Node> children) {
        for (final Node node : children) {
            if (!(node instanceof TagNode t) || t.key() == null) {
                throw new IllegalStateException(
                        "Mixed keyed and unkeyed children under one parent: all siblings must carry a key()");
            }
        }
    }
}
