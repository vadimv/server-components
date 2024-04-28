package rsp.dom;

import rsp.page.EventContext;
import rsp.page.RenderContext;
import rsp.ref.Ref;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;

public class DomTreeRenderContext implements RenderContext {
    protected final VirtualDomPath rootPath;

    protected final Deque<Tag> tagsStack = new ArrayDeque<>();
    protected VirtualDomPath domPath;

    private String docType;
    private Tag rootTag;

    public DomTreeRenderContext(VirtualDomPath rootPath) {
        this.rootPath = Objects.requireNonNull(rootPath);
    }

    @Override
    public void setDocType(final String docType) {
        this.docType = docType;
    }

    public String docType() {
        return docType;
    }

    public Tag rootTag() {
        return rootTag;
    }

    @Override
    public void openNode(final XmlNs xmlns, final String name, boolean isSelfClosing) {
        if (rootTag == null) {
            rootTag = new Tag(xmlns, name, isSelfClosing);
            tagsStack.push(rootTag);
            domPath = rootPath;
        } else {
            final Tag parent = tagsStack.peek();
            assert parent != null;
            final int nextChild = parent.children.size() + 1;
            final Tag newTag = new Tag(xmlns, name, isSelfClosing);
            parent.addChild(newTag);
            tagsStack.push(newTag);
            domPath = domPath.childNumber(nextChild);
        }
    }

    @Override
    public void closeNode(final String name, final boolean upgrade) {
        tagsStack.pop();
        domPath = domPath.parent().orElseThrow();
    }

    @Override
    public void setAttr(final XmlNs xmlNs, final String name, final String value, final boolean isProperty) {
        tagsStack.peek().addAttribute(name, value, isProperty);
    }

    @Override
    public void setStyle(final String name, final String value) {
        tagsStack.peek().addStyle(name, value);
    }

    @Override
    public void addTextNode(final String text) {
        tagsStack.peek().addChild(new Text(text));
    }


    @Override
    public void addEvent(final VirtualDomPath elementPath,
                         final String eventName,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final Event.Modifier modifier) {}

    @Override
    public void addEvent(final String eventName,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final Event.Modifier modifier) {}

    @Override
    public void addRef(final Ref ref) {}

    @Override
    public String toString() {
        if (rootTag == null) {
            throw new IllegalStateException("DOM tree not initialized");
        }
        final StringBuilder sb = new StringBuilder();
        if (docType != null) {
            sb.append(docType);
        }
        rootTag.appendString(sb);
        return sb.toString();
    }
}
