package rsp.dom;

import rsp.component.Component;
import rsp.page.LivePage;
import rsp.ref.Ref;
import rsp.page.EventContext;
import rsp.page.RenderContext;
import rsp.server.Path;
import rsp.stateview.ComponentView;
import rsp.stateview.NewState;
import rsp.util.Lookup;
import rsp.util.data.Tuple2;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public final class DomTreeRenderContext implements RenderContext {
    private final VirtualDomPath rootPath;
    private final Lookup stateOriginLookup;
    private final AtomicReference<LivePage> livePageContext;

    private final Deque<Tag> tagsStack = new ArrayDeque<>();
    private final Deque<Component<?, ?>> componentsStack = new ArrayDeque<>();

    private int statusCode;
    private Map<String, String> headers;
    private String docType;
    private Tag rootTag;
    private Component<?, ?> rootComponent;


    public DomTreeRenderContext(final VirtualDomPath rootPath,
                                final Lookup stateOriginLookup,
                                final AtomicReference<LivePage> livePageContext) {
        this.rootPath = Objects.requireNonNull(rootPath);
        this.stateOriginLookup = stateOriginLookup;
        this.livePageContext = Objects.requireNonNull(livePageContext);
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String docType() {
        return docType;
    }

    public Tag rootTag() {
        return rootTag;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Component<?, ?> rootComponent() {
        return rootComponent;
    }

    public int statusCode() {
        return statusCode;
    }

    @Override
    public void setStatusCode(final int statusCode) {
        this.statusCode = statusCode;
    }

    @Override
    public void setHeaders(final Map<String, String> headers) {
        this.headers = headers;
    }

    @Override
    public void setDocType(final String docType) {
        this.docType = docType;
    }

    @Override
    public void openNode(final XmlNs xmlns, final String name) {
        if (rootTag == null) {
            rootTag = new Tag(rootPath, xmlns, name);
            tagsStack.push(rootTag);
            setComponentRootTag(rootTag);
        } else {
            final Tag parent = tagsStack.peek();
            final int nextChild = parent.children.size() + 1;
            final Tag newTag = new Tag(parent.path.childNumber(nextChild), xmlns, name);
            parent.addChild(newTag);
            tagsStack.push(newTag);
            setComponentRootTag(newTag);
        }
    }

    private void setComponentRootTag(final Tag newTag) {
        final Component<?, ?> component = componentsStack.peek();
        if (component != null && component.tag == null)
        {
            component.tag = newTag;
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
        tagsStack.peek().addChild(new Text(tagsStack.peek().path, text));
    }

    @Override
    public void addEvent(final Optional<VirtualDomPath> elementPath,
                         final String eventType,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final Event.Modifier modifier) {
        final VirtualDomPath eventPath = elementPath.orElse(tagsStack.peek().path);
        final Event.Target eventTarget = new Event.Target(eventType, eventPath);
        final Component<?, ?> component = componentsStack.peek();
        assert component != null;
        component.addEvent(eventTarget, new Event(eventTarget, eventHandler, preventDefault, modifier));
    }

    @Override
    public void addRef(final Ref ref) {
        final Component<?, ?> component = componentsStack.peek();
        assert component != null;
        component.addRef(ref, tagsStack.peek().path);
    }

    @Override
    public <T, S> Tuple2<S, NewState<S>> openComponent(final Class<T> stateOriginClass,
                                                       final Function<T, CompletableFuture<S>> initialStateFunction,
                                                       final BiFunction<S, Path, Path> state2pathFunction,
                                                       final ComponentView<S> componentView) {
        final Component<T, S> newComponent = new Component<>(stateOriginLookup,
                                                             stateOriginClass,
                                                             initialStateFunction,
                                                             state2pathFunction,
                                                             componentView,
                                                            this,
                                                             livePageContext);
        final S initialState = newComponent.resolveState();
        if (rootComponent == null) {
            rootComponent = newComponent;
        } else {
            final Component<?, ?> parentComponent = componentsStack.peek();
            parentComponent.addChild(newComponent);
        }
        componentsStack.push(newComponent);
        return new Tuple2<>(initialState, newComponent);
    }

    public <T, S> void openComponent(Component<T, S> component) {
        if (rootComponent == null) {
            rootComponent = component;
        } else {
            final Component<?, ?> parentComponent = componentsStack.peek();
            parentComponent.addChild(component);
        }
        componentsStack.push(component);
    }

    @Override
    public void closeComponent() {
        componentsStack.pop();
    }

    @Override
    public RenderContext newSharedContext(final VirtualDomPath path) {
        return new DomTreeRenderContext(path, livePageContext.get().httpRequestLookup, livePageContext);
    }

    public VirtualDomPath rootPath() {
        return rootPath;
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


