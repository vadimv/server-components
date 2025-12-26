package rsp.util.html;

import rsp.dom.Node;
import rsp.dom.TagNode;
import rsp.dom.TextNode;
import rsp.dom.XmlNs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class HtmlParser {
    final Deque<TagNode> tagsStack = new ArrayDeque<>();
    final List<Node> rootNodes = new ArrayList<>();
    int rootNodeIndex = 0;

    public List<Node> parse(final String html) {
        final var doc =  org.jsoup.Jsoup.parse(html);

        doc.traverse(new org.jsoup.select.NodeVisitor() {
            @Override
            public void head(final org.jsoup.nodes.Node node, final int depth) {
                if (!(node instanceof org.jsoup.nodes.Document) && node instanceof org.jsoup.nodes.Element elementNode) {
                    final TagNode tag = new TagNode(XmlNs.html, elementNode.nodeName(),false);
                    node.attributes().forEach(attribute -> tag.addAttribute(attribute.getKey(), attribute.getValue(), true));
                    final TagNode parentTag = tagsStack.peek();
                    if (parentTag == null) {
                        rootNodes.add(tag);
                        rootNodeIndex++;
                    } else {
                        parentTag.addChild(tag);
                    }
                    tagsStack.push(tag);
                } else if (node instanceof org.jsoup.nodes.TextNode textNode) {
                    if (!textNode.isBlank()) {
                        final Node text = new TextNode(textNode.text());
                        final TagNode parentTag = tagsStack.peek();
                        if (parentTag == null) {
                            rootNodes.add(text);
                            rootNodeIndex++;
                        } else {
                            parentTag.addChild(text);
                        }
                    }
                }
            }

            @Override
            public void tail(final org.jsoup.nodes.Node node, final int depth) {
                if (!tagsStack.isEmpty() && node instanceof org.jsoup.nodes.Element) {
                    tagsStack.pop();
                }
            }
        });

        return rootNodes;
    }
}
