package rsp.dom;

import rsp.page.EventContext;
import rsp.page.RenderContext;
import rsp.ref.Ref;

import java.util.*;
import java.util.function.Consumer;

public class DomTreeRenderContext implements RenderContext {
    protected final VirtualDomPath parentDomPath;
    private String docType;

    protected final Deque<Tag> tagsStack = new ArrayDeque<>();
    protected List<Tag> rootNodes = new ArrayList<>();

    protected VirtualDomPath domPath;


    public DomTreeRenderContext(VirtualDomPath startDomPath) {
        this.parentDomPath = startDomPath;
        this.domPath = startDomPath;
    }

    @Override
    public void setDocType(final String docType) {
        this.docType = docType;
    }

    public String docType() {
        return docType;
    }

    public NodeList rootNodes() {
        return new NodeList(rootNodes);
    }

    @Override
    public void openNode(final XmlNs xmlns, final String name, boolean isSelfClosing) {
        final Tag parent = tagsStack.peek();
        if (rootNodes.isEmpty()) {
            Tag tag = new Tag(xmlns, name, isSelfClosing);
            tagsStack.push(tag);
            rootNodes.add(tag);
        } else {
            assert parent != null;
            final int nextChild = parent.children.size() + 1;
            domPath = domPath.childNumber(nextChild);
            final Tag newTag = new Tag(xmlns, name, isSelfClosing);
            parent.addChild(newTag);
            tagsStack.push(newTag);
        }
    }

    @Override
    public void closeNode(final String name, final boolean upgrade) {
        tagsStack.pop();
        domPath = domPath.parent();
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
        final StringBuilder sb = new StringBuilder();
        if (docType != null) {
            sb.append(docType); // TODO check
        }
        rootNodes.forEach(t -> t.appendString(sb));
        return sb.toString();
    }
}
