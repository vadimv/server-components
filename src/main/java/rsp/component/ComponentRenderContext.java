package rsp.component;

import rsp.dom.*;
import rsp.page.*;
import rsp.ref.Ref;
import rsp.server.RemoteOut;
import rsp.server.http.PageStateOrigin;

import java.util.*;
import java.util.function.*;

public class ComponentRenderContext extends DomTreeRenderContext implements RenderContextFactory {

    private final QualifiedSessionId sessionId;
    private final PageStateOrigin pageStateOrigin;
    private final RemoteOut remotePageMessagesOut;
    private final Object sessionLock;

    private final Deque<Component<?>> componentsStack = new ArrayDeque<>();
    private Component<?> rootComponent;

    public ComponentRenderContext(final QualifiedSessionId sessionId,
                                  final VirtualDomPath startDomPath,
                                  final PageStateOrigin pageStateOrigin,
                                  final RemoteOut remotePageMessagesOut,
                                  final Object sessionLock) {
        super(startDomPath);
        this.sessionId = Objects.requireNonNull(sessionId);
        this.pageStateOrigin = Objects.requireNonNull(pageStateOrigin);
        this.remotePageMessagesOut = Objects.requireNonNull(remotePageMessagesOut);
        this.sessionLock = Objects.requireNonNull(sessionLock);
    }
    
    @SuppressWarnings("unchecked")
    public <S> Component<S> rootComponent() {
        return (Component<S>) rootComponent;
    }

    @Override
    public void openNode(XmlNs xmlns, String name, boolean isSelfClosing) {
        super.openNode(xmlns, name, isSelfClosing);

        final Component<?> component = componentsStack.peek();
        assert component != null;
        final Tag tag = tagsStack.peek();
        assert tag != null;
        component.setStartNodeDomPath(domPath);
        component.addNode(domPath, tag);
    }

    @Override
    public void addEvent(final VirtualDomPath elementPath,
                         final String eventType,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final Event.Modifier modifier) {
        final Component<?> component = componentsStack.peek();
        assert component != null;
        component.addEvent(elementPath, eventType, eventHandler, preventDefault, modifier);
    }

    @Override
    public void addEvent(final String eventType,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final Event.Modifier modifier) {
        final Tag tag = tagsStack.peek();
        assert tag != null;
        addEvent(domPath, eventType, eventHandler, preventDefault, modifier);
    }

    @Override
    public void addRef(final Ref ref) {
        final Component<?> component = componentsStack.peek();
        assert component != null;
        final Tag tag = tagsStack.peek();
        assert tag != null;
        component.addRef(ref, domPath);
    }

    public <S> Component<S> openComponent(final ComponentFactory<S> componentFactory) {

        final Component<?> parent = componentsStack.peek();
        final ComponentPath componentPath = parent == null ?
                                   ComponentPath.ROOT_COMPONENT_PATH : parent.path().addChild(parent.directChildren().size() + 1);
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

    @Override
    public ComponentRenderContext newContext(final VirtualDomPath startDomPath) {
        return new ComponentRenderContext(sessionId,
                                          startDomPath,
                                          pageStateOrigin,
                                          remotePageMessagesOut,
                                          sessionLock);
    }
}


