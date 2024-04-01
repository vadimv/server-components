package rsp.dom;

import rsp.page.EventContext;
import rsp.page.RenderContext;
import rsp.ref.Ref;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class DomTreeRenderContext implements RenderContext {
    protected final VirtualDomPath rootDomPath;

    protected final Deque<Tag> tagsStack = new ArrayDeque<>();

    private String docType;
    private Tag rootTag;

    public DomTreeRenderContext(VirtualDomPath rootDomPath) {
        this.rootDomPath = Objects.requireNonNull(rootDomPath);
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
    public void openNode(final XmlNs xmlns, final String name) {
        if (rootTag == null) {
            rootTag = new Tag(rootDomPath, xmlns, name);
            tagsStack.push(rootTag);
        } else {
            final Tag parent = tagsStack.peek();
            assert parent != null;
            final int nextChild = parent.children.size() + 1;
            final Tag newTag = new Tag(parent.path().childNumber(nextChild), xmlns, name);
            parent.addChild(newTag);
            tagsStack.push(newTag);
        }
    }

    @Override
    public void closeNode(final String name, final boolean upgrade) {
        tagsStack.pop();
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
        tagsStack.peek().addChild(new Text(tagsStack.peek().path(), text));
    }


    @Override
    public void addEvent(final VirtualDomPath elementPath,
                         final String eventName,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final Event.Modifier modifier) {
        // NO-OP
    }

    @Override
    public void addEvent(final String eventName,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final Event.Modifier modifier) {
        // NO-OP
    }

    @Override
    public void addRef(final Ref ref) {
        // NO-OP
    }

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
