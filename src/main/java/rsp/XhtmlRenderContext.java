package rsp;

import rsp.dsl.RefDefinition;

import java.util.function.Consumer;

public class XhtmlRenderContext<S> implements RenderContext<S> {

    private final StringBuilder builder;
    private final TextPrettyPrinting prettyPrinting;

    private int lastOp = Op.OP_CLOSE;
    private int indentation = 0;
    public XhtmlRenderContext(TextPrettyPrinting prettyPrinting,
                              String docType) {
        this.prettyPrinting = prettyPrinting;
        this.builder = new StringBuilder(docType);
    }


    private void addIndentation() {
        if (prettyPrinting.enableAutoIndent) {
            if(builder.length() != 0) {
                builder.append(prettyPrinting.lineBreak);
            }

            int i = 0;
            while (i < indentation) {
                var j = 0;
                while (j < prettyPrinting.indentationSize) {
                    builder.append(prettyPrinting.indentationChar);
                    j += 1;
                }
                i += 1;
            }
        }
    }

    private void beforeOpenNode() {
        if (lastOp != Op.OP_CLOSE && lastOp != Op.OP_TEXT) {
            if (lastOp == Op.OP_STYLE) {
                builder.append('"');
            }
            builder.append('>');
/*            if(lastOp != OpText) {
                builder.append(prettyPrinting.lineBreak);
            }*/
        }
    }

    @Override
    public void openNode(XmlNs xmlns, String name) {
        beforeOpenNode();
        if(lastOp != Op.OP_TEXT) {
            addIndentation();
        }
        builder.append('<');
        builder.append(name);
        lastOp = Op.OP_OPEN;
        indentation += 1;
    }

    @Override
    public void closeNode(String name) {
        indentation -= 1;
        if (lastOp == Op.OP_ATTR || lastOp == Op.OP_OPEN) {
            builder.append('>');
        } else if (lastOp == Op.OP_STYLE) {
            builder.append("\">");
        } else if (lastOp != Op.OP_TEXT) {
            addIndentation();
        }
        builder.append('<');
        builder.append('/');
        builder.append(name);
        builder.append('>');
 /*       builder.append(prettyPrinting.lineBreak);*/
        lastOp = Op.OP_CLOSE;
    }

    @Override
    public void setAttr(XmlNs xmlNs, String name, String value) {
        if (lastOp == Op.OP_STYLE) {
            builder.append('"');
        }
        builder.append(' ');
        builder.append(name);
        builder.append('=');
        builder.append('"');
        builder.append(value);
        builder.append('"');
        lastOp = Op.OP_ATTR;
    }

    @Override
    public void setStyle(String name, String value) {
        if (lastOp != Op.OP_STYLE) {
            builder.append(" style=\"");
        }
        builder.append(name);
        builder.append(":");
        builder.append(value);
        builder.append(";");
        lastOp = Op.OP_STYLE;
    }

    @Override
    public void addTextNode(String text) {
        beforeOpenNode();
        //addIndentation();
        builder.append(text);
        //builder.append(prettyPrinting.lineBreak);
        lastOp = Op.OP_TEXT;
    }

    @Override
    public void addEvent(String eventType, Consumer<EventContext> eventHandler) {
        // no-op
    }

    @Override
    public void addRef(Ref ref) {
        // no-op
    }

    @Override
    public String toString() {
        return builder.toString();
    }
}


