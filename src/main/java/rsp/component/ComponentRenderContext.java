package rsp.component;

import rsp.dom.*;
import rsp.page.*;
import rsp.ref.Ref;
import rsp.server.Path;
import rsp.server.RemoteOut;
import rsp.server.http.HttpStateOrigin;
import rsp.server.http.PageStateOrigin;
import rsp.server.http.RelativeUrl;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

public class ComponentRenderContext extends DomTreeRenderContext implements RenderContextFactory {

    private final PageStateOrigin pageStateOrigin;
    private final RemoteOut remotePageMessagesOut;

    private final Deque<Component<?>> componentsStack = new ArrayDeque<>();
    private Component<?> rootComponent;

    public ComponentRenderContext(final VirtualDomPath rootDomPath,
                                  final PageStateOrigin pageStateOrigin,
                                  final RemoteOut remotePageMessagesOut) {
        super(rootDomPath);
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
    public void addEvent(final Optional<VirtualDomPath> elementPath,
                         final String eventType,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final Event.Modifier modifier) {
        final VirtualDomPath eventPath = elementPath.orElse(tagsStack.peek().path());
        final Event.Target eventTarget = new Event.Target(eventType, eventPath);
        final Component<?> component = componentsStack.peek();
        assert component != null;
        component.addEvent(eventTarget, new Event(eventTarget, eventHandler, preventDefault, modifier));
    }

    @Override
    public void addRef(final Ref ref) {
        final Component<?> component = componentsStack.peek();
        assert component != null;
        component.addRef(ref, tagsStack.peek().path());
    }

    public <S> Component<S> openComponent(final Object key,
                                          final Function<HttpStateOrigin, CompletableFuture<? extends S>> resolveStateFunction,
                                          final BiFunction<S, Path, Path> state2pathFunction,
                                          final ComponentView<S> componentView) {
        final Supplier<CompletableFuture<? extends S>> resolveStateSupplier = () -> resolveStateFunction.apply(pageStateOrigin.httpStateOrigin());
        final Component<S> newComponent = new Component<>(key,
                                                          resolveStateSupplier,
                                                          componentView,
                                                         this,
                                                          remotePageMessagesOut,
                                                          state -> {
                                                              final RelativeUrl oldRelativeUrl = pageStateOrigin.getRelativeUrl();
                                                              final Path oldPath = oldRelativeUrl.path();
                                                              final Path newPath = state2pathFunction.apply(state, oldPath);
                                                              if (!newPath.equals(oldPath)) {
                                                                  pageStateOrigin.setRelativeUrl(new RelativeUrl(newPath, oldRelativeUrl.query(), oldRelativeUrl.fragment()));
                                                                  remotePageMessagesOut.pushHistory(newPath.toString());
                                                              }
                                                          });
        openComponent(newComponent);
        return newComponent;
    }

    public <S> void openComponent(final Component<S> component) {
        if (rootComponent == null) {
            rootComponent = component;
        } else {
            final Component<?> parentComponent = componentsStack.peek();
            parentComponent.addChild(component);
        }
        componentsStack.push(component);
    }

    public void closeComponent() {
        componentsStack.pop();
    }

    @Override
    public ComponentRenderContext newContext(final VirtualDomPath domPath) {
        return new ComponentRenderContext(domPath,
                                          pageStateOrigin,
                                          remotePageMessagesOut);
    }

    @Override
    public ComponentRenderContext newContext() {
        return new ComponentRenderContext(rootDomPath,
                                          pageStateOrigin,
                                          remotePageMessagesOut);
    }
}


