package rsp;

import rsp.dsl.EventDefinition;

import java.util.function.Consumer;

public class XhtmlRenderContext<S> implements RenderContext<S> {

    public final static int OP_OPEN = 1;
    public final static int OP_CLOSE = 2;
    public final static int OP_ATTR = 3;
    public final static int OP_TEXT = 4;
    public final static int OP_LAST_ATTR = 5;
    public final static int OP_END = 6;
    public final static int OP_STYLE = 100;

    private final StringBuilder builder;
    private final TextPrettyPrinting prettyPrinting;

    private int lastOp = OP_CLOSE;
    private int indentation = 0;
    public XhtmlRenderContext(TextPrettyPrinting prettyPrinting,
                              String docType) {
        this.prettyPrinting = prettyPrinting;
        this.builder = new StringBuilder(docType);
    }


    private void addIndentation() {
        if (prettyPrinting.enableAutoIndent) {
            if (builder.length() != 0) {
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
        if (lastOp != OP_CLOSE && lastOp != OP_TEXT) {
            if (lastOp == OP_STYLE) {
                builder.append('"');
            }
            builder.append('>');
/*            if (lastOp != OpText) {
                builder.append(prettyPrinting.lineBreak);
            }*/
        }
    }

    @Override
    public void openNode(XmlNs xmlns, String name) {
        beforeOpenNode();
        if (lastOp != OP_TEXT) {
            addIndentation();
        }
        builder.append('<');
        builder.append(name);
        lastOp = OP_OPEN;
        indentation += 1;
    }

    @Override
    public void closeNode(String name) {
        indentation -= 1;
        if (lastOp == OP_ATTR || lastOp == OP_OPEN) {
            builder.append('>');
        } else if (lastOp == OP_STYLE) {
            builder.append("\">");
        } else if (lastOp != OP_TEXT) {
            addIndentation();
        }
        builder.append('<');
        builder.append('/');
        builder.append(name);
        builder.append('>');
 /*       builder.append(prettyPrinting.lineBreak);*/
        lastOp = OP_CLOSE;
    }

    @Override
    public void setAttr(XmlNs xmlNs, String name, String value) {
        if (lastOp == OP_STYLE) {
            builder.append('"');
        }
        builder.append(' ');
        builder.append(name);
        builder.append('=');
        builder.append('"');
        builder.append(value);
        builder.append('"');
        lastOp = OP_ATTR;
    }

    @Override
    public void setStyle(String name, String value) {
        if (lastOp != OP_STYLE) {
            builder.append(" style=\"");
        }
        builder.append(name);
        builder.append(":");
        builder.append(value);
        builder.append(";");
        lastOp = OP_STYLE;
    }

    @Override
    public void addTextNode(String text) {
        beforeOpenNode();
        //addIndentation();
        builder.append(text);
        //builder.append(prettyPrinting.lineBreak);
        lastOp = OP_TEXT;
    }

    @Override
    public void addEvent(EventDefinition.EventElementMode mode,
                         String eventType,
                         Consumer<EventContext> eventHandler,
                         Event.Modifier modifier) {
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


