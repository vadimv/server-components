package rsp.dom;

import rsp.util.html.HtmlEscape;

import java.util.Objects;
import java.util.Set;

/**
 * A mutable HTML string which is built from nodes trees.
 */
public class HtmlBuilder {
    private final StringBuilder sb;
    private final boolean escapeText;

    /** HTML raw text elements whose content must not be HTML-escaped. */
    private static final Set<String> RAW_TEXT_ELEMENTS = Set.of("script", "style");

    private boolean inRawTextElement;

    public HtmlBuilder(final StringBuilder sb) {
        this(sb, false);
    }

    public HtmlBuilder(final StringBuilder sb, final boolean escapeText) {
        this.sb = Objects.requireNonNull(sb);
        this.escapeText = escapeText;
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
        Objects.requireNonNull(node);
        if (node instanceof TagNode tagNode) {
            buildHtml(tagNode);
        } else if (node instanceof TextNode textNode) {
            buildHtml(textNode);
        }
     }

    private void buildHtml(final TagNode tag) {
        final boolean isRawText = RAW_TEXT_ELEMENTS.contains(tag.name);
        final boolean previousInRawText = inRawTextElement;
        if (isRawText) {
            inRawTextElement = true;
        }

        sb.append('<');
        sb.append(tag.name);
        AttributeNode deferredInnerHtml = null;
        if (tag.attributes.size() > 0) {
            for (final AttributeNode attribute: tag.attributes) {
                if ("innerHTML".equals(attribute.name()) && attribute.isProperty()) {
                    deferredInnerHtml = attribute;
                    continue;
                }
                sb.append(' ');
                sb.append(attribute.name());
                sb.append('=');
                sb.append('"');
                sb.append(attribute.value());
                sb.append('"');
            }
        }
        // Strip innerHTML property from the SSR virtual DOM tree so the first
        // client-side diff will detect it as new and apply it via WebSocket.
        if (deferredInnerHtml != null && escapeText) {
            tag.attributes.remove(deferredInnerHtml);
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

        inRawTextElement = previousInRawText;
    }

    private void buildHtml(TextNode textNode) {
        final boolean shouldEscape = escapeText && !inRawTextElement;
        textNode.parts.forEach(part -> sb.append(shouldEscape ? HtmlEscape.escape(part) : part));
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
