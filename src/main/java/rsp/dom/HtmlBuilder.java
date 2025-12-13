package rsp.dom;

import java.util.Objects;

/**
 * A mutable HTML string which is built from nodes trees.
 */
public class HtmlBuilder {
    private final StringBuilder sb;

    public HtmlBuilder(final StringBuilder sb) {
        this.sb = Objects.requireNonNull(sb);
    }

    /**
     * Clears the HTML text.
     */
    public void reset() {
        sb.setLength(0);
    }

    /**
     * Appends HTML text with a fragment built from a DOM nodes tree.
     * @param node a root node of a DOM tree to add to the result HTML
     */
    public void buildHtml(final Node node) {
        if (node instanceof TagNode tagNode) {
            buildHtml(tagNode);
        } else if (node instanceof TextNode textNode) {
            buildHtml(textNode);
        }
     }

    private void buildHtml(final TagNode tag) {
        sb.append('<');
        sb.append(tag.name);
        if (!tag.styles.isEmpty()) {
            sb.append(" style=\"");
            for (final Style style: tag.styles) {
                sb.append(style.name());
                sb.append(":");
                sb.append(style.value());
                sb.append(";");
            }
            sb.append('"');
        }
        if (tag.attributes.size() > 0) {
            for (final AttributeNode attribute: tag.attributes) {
                sb.append(' ');
                sb.append(attribute.name());
                sb.append('=');
                sb.append('"');
                sb.append(attribute.value());
                sb.append('"');
            }
        }
        if (tag.isSelfClosing) {
            sb.append(" />");
        } else {
            sb.append('>');

            if (tag.children.size() > 0) {
                for (final Node childNode: tag.children) {
                    if (childNode instanceof TagNode childTag) {
                        buildHtml(childTag);
                    } else if (childNode instanceof TextNode childTextNode) {
                        buildHtml(childTextNode);
                    }
                }
            }

            sb.append("</");
            sb.append(tag.name);
            sb.append('>');
        }
    }

    private void buildHtml(TextNode textNode) {
        textNode.parts.forEach(part -> sb.append(part));
    }

    /**
     * Gets result HTML as a string.
     * @return a built HTML
     */
    @Override
    public String toString() {
        return sb.toString();
    }
}
