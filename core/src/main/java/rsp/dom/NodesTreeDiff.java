package rsp.dom;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * This class compares two DOM trees and on the base of their difference generates changes instructions for
 * a transformation from the first tree or forest to the second tree or forest.
 * @see DefaultDomChangesContext.DomChange for an atomic transformation
 */
public final class NodesTreeDiff {

    /**
     * Diffs two single root trees.
     * @param tree1 the first tree
     * @param tree2 the second tree
     * @param tagPath a start tag's position path, can be a root or a subtree's start position when comparing subtrees
     * @param changesPerformer an abstraction for a destination of transformation instructions, e.g. a mutable collector
     *                         after completing of this operation should "know" how to transform the first tree to the second one
     * @param htmlBuilder a helper mutable object
     */
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
        if (!tree1.name.equals(tree2.name)) {
            changesPerformer.removeNode(tagPath.parent(), tagPath);
            createTag(tree2, tagPath, changesPerformer, htmlBuilder);
        } else {
            diffAttributes(tree1.attributes, tree2.attributes, tagPath, changesPerformer);
            diffChildren(tree1.children, tree2.children, tagPath.incLevel(), changesPerformer, htmlBuilder);
        }
    }

    /**
     * Diffs two multiroot ordered forests.
     * @param trees1 the first forest
     * @param trees2 the second forest
     * @param startNodePath a start tag's position path, can be a root or a subtree's start position when comparing forests under subtrees
     * @param changesPerformer an abstraction for a destination of transformation instructions, e.g. a mutable collector,
     *                         after completing of this operation should "know" how to transform the first forest to the second one
     * @param htmlBuilder a helper mutable object
     */
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

        final ListIterator<? extends Node> nodesIterator1 = trees1.listIterator();
        final ListIterator<? extends Node> nodesIterator2 = trees2.listIterator();
        TreePositionPath path = startNodePath;
        while(nodesIterator1.hasNext() || nodesIterator2.hasNext()) {
            if (nodesIterator1.hasNext() && nodesIterator2.hasNext()) {
                final Node node1 = nodesIterator1.next();
                final Node node2 = nodesIterator2.next();
                if (node1 instanceof TagNode tagNode1 && node2 instanceof TagNode tagNode2) {
                    diff(tagNode1, tagNode2, path, changesPerformer, htmlBuilder);
                } else if (node2 instanceof TagNode t) {
                    changesPerformer.removeNode(path.parent(), path);
                    createTag(t, path, changesPerformer, htmlBuilder);
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
                    createTag(tagNode2, path, changesPerformer, htmlBuilder);
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

    private static void diffAttributes(final CopyOnWriteArraySet<AttributeNode> attributes1,
                                       final CopyOnWriteArraySet<AttributeNode> attributes2,
                                       final TreePositionPath nodePath,
                                       final DomChangesContext changesPerformer) {
        final Set<AttributeNode> attrs1 = new CopyOnWriteArraySet<>(attributes1);
        final Set<AttributeNode> attrs2 = new CopyOnWriteArraySet<>(attributes2);
        attrs1.removeAll(attributes2);
        attrs1.forEach(attribute -> changesPerformer.removeAttr(nodePath, XmlNs.html, attribute.name(), attribute.isProperty()));
        attrs2.removeAll(attributes1);
        attrs2.forEach(attribute -> changesPerformer.setAttr(nodePath, XmlNs.html, attribute.name(), attribute.value(), attribute.isProperty()));
    }

    private static void createTag(final TagNode tag,
                                  final TreePositionPath nodePath,
                                  final DomChangesContext changesPerformer,
                                  final HtmlBuilder htmlBuilder) {
        changesPerformer.createTag(nodePath, tag.xmlns, tag.name);

        for (final AttributeNode attribute: tag.attributes) {
            changesPerformer.setAttr(nodePath, XmlNs.html, attribute.name(), attribute.value(), attribute.isProperty());
        }

        TreePositionPath path = nodePath.incLevel();
        for (final Node child:tag.children) {
            if (child instanceof TagNode t) {
                createTag(t, path, changesPerformer, htmlBuilder);
            } else if (child instanceof TextNode) {
                htmlBuilder.reset();
                htmlBuilder.buildHtml(child);
                changesPerformer.createText(nodePath, path, htmlBuilder.toString());
            }
            path = path.incSibling();
        }
    }
}
