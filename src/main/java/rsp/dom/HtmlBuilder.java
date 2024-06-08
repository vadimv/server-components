package rsp.dom;

public class HtmlBuilder {

    private final StringBuilder sb;
    private final String docType;
    private final boolean isInline;
    private final String paddingStep;

    public HtmlBuilder(final StringBuilder sb, final String docType, final HtmlLayout layout) {
        this.sb = sb;
        this.docType = docType;
        this.isInline = layout instanceof HtmlLayout.Inline;
        if (layout instanceof HtmlLayout.WithIndent indentLayout) {
            paddingStep = " ".repeat(indentLayout.indentSpaces());
        } else {
            paddingStep = "";
        }
    }

    public HtmlBuilder(final StringBuilder sb) {
        this(sb, "", HtmlLayout.INLINE);
    }

    public void reset() {
        sb.setLength(0);
    }

    public void buildHtml(final Node node) {
        if (node instanceof HtmlElement tagNode) {
            sb.append(docType);
            if (!isInline) {
                sb.append("\n");
            }
            buildHtml(tagNode, 0);
        } else if (node instanceof Text textNode) {
            buildHtml(textNode);
        }
     }

    private void buildHtml(final HtmlElement tag, int level) {
        if (!isInline) {
            sb.append(padString(level));
        }

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
        int childrenTagsCount = 0;
        if (tag.isSelfClosing()) {
            sb.append(" />");
        } else {
            sb.append('>');
            if (tag.children.size() > 0) {
                for (final Node childNode: tag.children) {
                    if (childNode instanceof HtmlElement childTag) {
                        if (!isInline) {
                            sb.append("\n");
                        }

                        buildHtml(childTag, level + 1);
                        childrenTagsCount++;
                    } else if (childNode instanceof Text childTextNode) {
                        buildHtml(childTextNode);
                    }
                }
            }
            if (childrenTagsCount > 0) {
                if (!isInline) {
                    sb.append("\n" + padString(level));
                }
            }
            sb.append("</");
            sb.append(tag.name);
            sb.append(">");
        }
    }

    private String padString(int level) {
        return paddingStep.repeat(level);
    }

    private void buildHtml(Text textNode) {
        textNode.parts.forEach(part -> sb.append(part));
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
