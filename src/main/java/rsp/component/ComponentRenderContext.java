package rsp.component;

import rsp.dom.*;
import rsp.page.*;
import rsp.ref.Ref;
import rsp.server.RemoteOut;
import rsp.server.http.PageStateOrigin;

import java.util.*;
import java.util.function.*;

public class ComponentRenderContext implements RenderContextFactory {

    private static final TreePositionPath ROOT_COMPONENT_PATH = TreePositionPath.of("1");

    protected final QualifiedSessionId sessionId;
    protected final PageStateOrigin pageStateOrigin;
    protected final RemoteOut remotePageMessagesOut;
    protected final Object sessionLock;

    private final Deque<Tag> tagsStack = new ArrayDeque<>();
    private final List<TreePositionPath> rootNodesPaths = new ArrayList<>();
    private final Deque<Component<?>> componentsStack = new ArrayDeque<>();
    private String docType = "";
    private TreePositionPath domPath;

    private Component<?> rootComponent;

    public ComponentRenderContext(final QualifiedSessionId sessionId,
                                  final TreePositionPath startDomPath,
                                  final PageStateOrigin pageStateOrigin,
                                  final RemoteOut remotePageMessagesOut,
                                  final Object sessionLock) {
        this.domPath = Objects.requireNonNull(startDomPath);
        this.sessionId = Objects.requireNonNull(sessionId);
        this.pageStateOrigin = Objects.requireNonNull(pageStateOrigin);
        this.remotePageMessagesOut = Objects.requireNonNull(remotePageMessagesOut);
        this.sessionLock = Objects.requireNonNull(sessionLock);
    }

    public void setDocType(final String docType) {
        this.docType = docType;
    }

    public String docType() {
        return docType;
    }

    public <S> Component<S> openComponent(final ComponentFactory<S> componentFactory) {
        final Component<?> parent = componentsStack.peek();
        final TreePositionPath componentPath = parent == null ?
                ROOT_COMPONENT_PATH : parent.path().addChild(parent.directChildren().size() + 1);
        final Component<S> newComponent = componentFactory.createComponent(sessionId,
                                                                           componentPath,
                                                                           pageStateOrigin,
                                                                           this,
                                                                           remotePageMessagesOut,
                                                                           sessionLock);
        openComponent(newComponent);
        return newComponent;
    }

    public <S> void openComponent(final Component<S> component) {
        if (rootComponent == null) {
            rootComponent = component;
        } else {
            final Component<?> parentComponent = componentsStack.peek();
            assert parentComponent != null;
            parentComponent.addChild(component);
        }
        componentsStack.push(component);
    }

    public void closeComponent() {
        componentsStack.pop();
    }

    public void openNode(String name, boolean isSelfClosing) {
        final Component<?> component = componentsStack.peek();
        assert component != null;

        final Tag parent = tagsStack.peek();
        final Tag tag = new Tag(name, isSelfClosing);
        if (parent == null) {
            if (!component.isRootNodesEmpty()) {
                final TreePositionPath prevTag = rootNodesPaths.get(rootNodesPaths.size() - 1);
                domPath = prevTag.incSibling();
            }
            rootNodesPaths.add(domPath);
        } else {
            final int nextChild = parent.children.size() + 1;
            domPath = domPath.addChild(nextChild);
            parent.addChild(tag);
        }
        tagsStack.push(tag);

        component.notifyNodeOpened(domPath, tag);
    }

    public void closeNode(final String name, final boolean upgrade) {
        tagsStack.pop();
        domPath = domPath.parent();
    }

    public void addTextNode(final String text) {
        final Component<?> component = componentsStack.peek();
        assert component != null;

        final Tag parentTag = tagsStack.peek();
        if (parentTag == null) {
            if (!component.isRootNodesEmpty()) {
                if (component.getLastRootNode() instanceof Text prevTextNode) {
                    prevTextNode.addPart(text);
                } else {
                    final TreePositionPath prevTag = rootNodesPaths.get(rootNodesPaths.size() - 1);
                    domPath = prevTag.incSibling();
                    rootNodesPaths.add(domPath);
                    component.notifyNodeOpened(domPath, new Text(text));
                }
            } else {
                rootNodesPaths.add(domPath);
                component.notifyNodeOpened(domPath, new Text(text));
            }
        } else {
            if (!parentTag.children.isEmpty() && parentTag.children.get(parentTag.children.size() - 1) instanceof Text prevTextNode) {
                prevTextNode.addPart(text);
            } else {
                final int nextChild = parentTag.children.size() + 1;
                domPath = domPath.addChild(nextChild);
                final Text newText = new Text(text);
                parentTag.addChild(newText);
                component.notifyNodeOpened(domPath, newText);
                domPath  = domPath.parent();
            }
        }
    }

    public void setAttr(final String name, final String value, final boolean isProperty) {
        tagsStack.peek().addAttribute(name, value, isProperty);
    }

    public void setStyle(final String name, final String value) {
        tagsStack.peek().addStyle(name, value);
    }

    public void addEvent(final TreePositionPath elementPath,
                         final String eventType,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final Event.Modifier modifier) {
        final Component<?> component = componentsStack.peek();
        assert component != null;
        component.addEvent(elementPath, eventType, eventHandler, preventDefault, modifier);
    }

    public void addEvent(final String eventType,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final Event.Modifier modifier) {
        final Tag tag = tagsStack.peek();
        assert tag != null;
        addEvent(domPath, eventType, eventHandler, preventDefault, modifier);
    }

    public void addRef(final Ref ref) {
        final Component<?> component = componentsStack.peek();
        assert component != null;
        final Tag tag = tagsStack.peek();
        assert tag != null;
        component.addRef(ref, domPath);
    }

    @Override
    public ComponentRenderContext newContext(final TreePositionPath startDomPath) {
        return new ComponentRenderContext(sessionId,
                                          startDomPath,
                                          pageStateOrigin,
                                          remotePageMessagesOut,
                                          sessionLock);
    }

    public String html() {
        final StringBuilder sb = new StringBuilder();
        final HtmlBuilder hb = new HtmlBuilder(sb, docType, HtmlLayout.INLINE);
        if (rootComponent != null) {
            rootComponent.html(hb);
        }
        return hb.toString();
    }

    public List<Event> recursiveEvents() {
        if (rootComponent != null) {
            return rootComponent.recursiveEvents();
        } else {
            return List.of();
        }
    }

    public void shutdown() {
        if (rootComponent != null) {
            rootComponent.unmount();
        }
    }

    public Map<Ref, TreePositionPath> recursiveRefs() {
        if (rootComponent != null) {
            return rootComponent.recursiveRefs();
        } else {
            return Map.of();
        }
    }
}


