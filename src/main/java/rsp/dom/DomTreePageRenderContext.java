package rsp.dom;

import rsp.ref.Ref;
import rsp.page.EventContext;
import rsp.page.PageRenderContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public final class DomTreePageRenderContext implements PageRenderContext {

    public final ConcurrentHashMap<Event.Target, Event> events = new ConcurrentHashMap();
    public final ConcurrentHashMap<Ref, VirtualDomPath> refs = new ConcurrentHashMap();

    private int statusCode;
    private Map<String, String> headers;
    private String docType;
    private Tag root;

    private Deque<Tag> tagsStack = new ArrayDeque<>();
    private Deque<Consumer<Object>> componentsStack = new ArrayDeque<>();

    public DomTreePageRenderContext(Consumer<Object> initialSetState) {
        componentsStack.push(initialSetState);
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String docType() {
        return docType;
    }

    public Tag root() {
        return root;
    }

    public int statusCode() {
        return statusCode;
    }


    @Override
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    @Override
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    @Override
    public void setDocType(String docType) {
        this.docType = docType;
    }

    @Override
    public void openNode(XmlNs xmlns, String name) {
        if (root == null) {
            root = new Tag(VirtualDomPath.DOCUMENT, xmlns, name);
            tagsStack.push(root);
        } else {
            final Tag parent = tagsStack.peek();
            final int nextChild = parent.children.size() + 1;
            final Tag newTag = new Tag(parent.path.childNumber(nextChild), xmlns, name);
            parent.addChild(newTag);
            tagsStack.push(newTag);
        }
    }

    @Override
    public void closeNode(String name,  boolean upgrade) {
        tagsStack.pop();
    }

    @Override
    public void setAttr(XmlNs xmlNs, String name, String value, boolean isProperty) {
        tagsStack.peek().addAttribute(name, value, isProperty);
    }

    @Override
    public void setStyle(String name, String value) {
        tagsStack.peek().addStyle(name, value);
    }

    @Override
    public void addTextNode(String text) {
        tagsStack.peek().addChild(new Text(tagsStack.peek().path, text));
    }

    @Override
    public <S> void addEvent(Optional<VirtualDomPath> elementPath,
                             String eventType,
                             Consumer<EventContext<S>> eventHandler,
                             boolean preventDefault,
                             Event.Modifier modifier) {
        final VirtualDomPath eventPath = elementPath.orElse(tagsStack.peek().path);
        final Event.Target eventTarget = new Event.Target(eventType, eventPath);
        final Consumer<?> setState = componentsStack.peek();
        events.put(eventTarget, new Event(eventTarget, eventHandler, preventDefault, modifier, setState));
    }

    @Override
    public void addRef(Ref ref) {
        refs.put(ref, tagsStack.peek().path);
    }

    @Override
    public void openComponent(Function<Object, Object> f) {
        final Consumer<Object> parentComponent= componentsStack.peek();
        componentsStack.push(s -> parentComponent.accept(f.apply(s)));
    }

    @Override
    public void openComponent() {
        componentsStack.push(s -> {});
    }

    @Override
    public void closeComponent() {
        componentsStack.pop();
    }

    @Override
    public String toString() {
        if (root == null) {
            throw new IllegalStateException("DOM tree not initialized");
        }
        final StringBuilder sb = new StringBuilder();
        if (docType != null) {
            sb.append(docType);
        }
        root.appendString(sb);
        return sb.toString();
    }
}


