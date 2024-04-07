package rsp.component;

import rsp.dom.*;
import rsp.page.*;
import rsp.ref.Ref;
import rsp.server.RemoteOut;
import rsp.server.http.PageStateOrigin;
import rsp.server.http.RelativeUrl;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

public class ComponentRenderContext extends DomTreeRenderContext implements RenderContextFactory {

    private final Object sessionId;
    private final PageStateOrigin pageStateOrigin;
    private final RemoteOut remotePageMessagesOut;

    private final Deque<Component<?>> componentsStack = new ArrayDeque<>();
    private Component<?> rootComponent;

    public ComponentRenderContext(final Object sessionId,
                                  final VirtualDomPath rootDomPath,
                                  final PageStateOrigin pageStateOrigin,
                                  final RemoteOut remotePageMessagesOut) {
        super(rootDomPath);
        this.sessionId = Objects.requireNonNull(sessionId);
        this.pageStateOrigin = Objects.requireNonNull(pageStateOrigin);
        this.remotePageMessagesOut = Objects.requireNonNull(remotePageMessagesOut);
    }
    
    @SuppressWarnings("unchecked")
    public <S> Component<S> rootComponent() {
        return (Component<S>) rootComponent;
    }

    @Override
    public void openNode(XmlNs xmlns, String name) {
        super.openNode(xmlns, name);
        trySetCurrentComponentRootTag(tagsStack.peek());
    }

    private void trySetCurrentComponentRootTag(final Tag newTag) {
        final Component<?> component = componentsStack.peek();
        if (component != null) {
            component.setRootTagIfNotSet(newTag);
        }
    }

    @Override
    public void addEvent(final VirtualDomPath elementPath,
                         final String eventType,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final Event.Modifier modifier) {
        final Event.Target eventTarget = new Event.Target(eventType, elementPath);
        final Component<?> component = componentsStack.peek();
        assert component != null;
        component.addEvent(new Event(eventTarget, eventHandler, preventDefault, modifier));
    }

    @Override
    public void addEvent(final String eventType,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final Event.Modifier modifier) {
        final Tag tag = tagsStack.peek();
        assert tag != null;
        addEvent(tag.path(), eventType, eventHandler, preventDefault, modifier);
    }

    @Override
    public void addRef(final Ref ref) {
        final Component<?> component = componentsStack.peek();
        assert component != null;
        final Tag tag = tagsStack.peek();
        assert tag != null;
        component.addRef(ref, tag.path());
    }

    public <S> Component<S> openComponent(final Object componentType,
                                          final ComponentStateSupplier<S> stateSupplier,
                                          final BeforeRenderCallback<S> beforeRenderCallback,
                                          final ComponentView<S> componentView,
                                          final StateAppliedCallback<S> newStateAppliedCallback,
                                          final UnmountCallback<S> unmountCallback) {

        final Component<?> parent = componentsStack.peek();
        final ComponentPath path = parent == null ?
                                   ComponentPath.ROOT_COMPONENT_PATH : parent.path().addChild(parent.directChildren().size() + 1);
        final ComponentCompositeKey key = new ComponentCompositeKey(sessionId, componentType, path);
        final Supplier<CompletableFuture<? extends S>> resolveStateSupplier = () -> stateSupplier.getState(key,
                                                                                                           pageStateOrigin.httpStateOrigin());
        final Component<S> newComponent = new Component<>(key,
                                                          resolveStateSupplier,
                                                          beforeRenderCallback,
                                                          componentView,
                                                          newStateAppliedCallback,
                                                          unmountCallback,
                                                         this,
                                                          remotePageMessagesOut);
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
    public ComponentRenderContext newContext(final VirtualDomPath domPath) {
        return new ComponentRenderContext(sessionId,
                                          domPath,
                                          pageStateOrigin,
                                          remotePageMessagesOut);
    }

    @Override
    public ComponentRenderContext newContext() {
        return new ComponentRenderContext(sessionId,
                                          rootDomPath,
                                          pageStateOrigin,
                                          remotePageMessagesOut);
    }

    public RelativeUrl getRelativeUrl() {
        return pageStateOrigin.getRelativeUrl();
    }

    public void setRelativeUrl(RelativeUrl relativeUrl) {
        pageStateOrigin.setRelativeUrl(relativeUrl);
        remotePageMessagesOut.pushHistory(relativeUrl.path().toString());
    }
}


