package rsp.dom;

/**
 * Represents a DOM node.
 */
public sealed interface Node extends Segment permits TagNode, TextNode, AttributeNode {
}
