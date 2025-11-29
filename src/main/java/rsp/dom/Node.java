package rsp.dom;

public sealed interface Node extends Segment permits TagNode, TextNode, AttributeNode {
}
