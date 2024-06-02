package rsp.dom;

public class HtmlBuilder {
    private final StringBuilder sb;

    public HtmlBuilder(final StringBuilder sb) {
        this.sb = sb;
    }

    public void reset() {
        sb.setLength(0);
    }

    public void buildHtml(final Node node) {
        if (node instanceof Tag tagNode) {
            buildHtml(tagNode);
        } else if (node instanceof Text textNode) {
            buildHtml(textNode);
        }
     }

    private void buildHtml(final Tag tag) {
        sb.append('<');
        sb.append(tag.name);
        if (tag.styles.size() > 0) {
            sb.append(" style=\"");
            for (final Style style: tag.styles) {
                sb.append(style.name);
                sb.append(":");
                sb.append(style.value);
                sb.append(";");
            }
            sb.append('"');
        }
        if (tag.attributes.size() > 0) {
            for (final Attribute attribute: tag.attributes) {
                sb.append(' ');
                sb.append(attribute.name);
                sb.append('=');
                sb.append('"');
                sb.append(attribute.value);
                sb.append('"');
            }
        }
        if (tag.isSelfClosing) {
            sb.append(" />");
        } else {
            sb.append('>');

            if (tag.children.size() > 0) {
                for (final Node childNode: tag.children) {
                    if (childNode instanceof Tag childTag) {
                        buildHtml(childTag);
                    } else if (childNode instanceof Text childTextNode) {
                        buildHtml(childTextNode);
                    }
                }
            }

            sb.append("</");
            sb.append(tag.name);
            sb.append('>');
        }
    }

    private void buildHtml(Text textNode) {
        textNode.parts.forEach(part -> sb.append(part));
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
