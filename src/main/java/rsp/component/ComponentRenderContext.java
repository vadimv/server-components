package rsp.component;

import rsp.dom.*;
import rsp.page.*;
import rsp.ref.Ref;
import rsp.server.Path;
import rsp.server.http.HttpStateOriginLookup;
import rsp.server.http.HttpStateOriginProvider;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class ComponentRenderContext extends DomTreeRenderContext implements RenderContextFactory {

    private final Path baseUrlPath;
    private final HttpStateOriginLookup httpStateOriginLookup;
    private final TemporaryBufferedPageCommands remotePageMessagesOut;

    private final Deque<Component<?, ?>> componentsStack = new ArrayDeque<>();
    private Component<?, ?> rootComponent;

    public ComponentRenderContext(final VirtualDomPath rootDomPath,
                                  final Path baseUrlPath,
                                  final HttpStateOriginLookup httpStateOriginLookup,
                                  final TemporaryBufferedPageCommands remotePageMessagesOut) {
        super(rootDomPath);
        this.baseUrlPath = Objects.requireNonNull(baseUrlPath);
        this.httpStateOriginLookup = Objects.requireNonNull(httpStateOriginLookup);
        this.remotePageMessagesOut = Objects.requireNonNull(remotePageMessagesOut);
    }

    @SuppressWarnings("unchecked")
    public <T, S> Component<T, S> rootComponent() {
        return (Component<T, S>) rootComponent;
    }


    @Override
    public void openNode(XmlNs xmlns, String name) {
        super.openNode(xmlns, name);
        trySetCurrentComponentRootTag(tagsStack.peek());
    }

    private void trySetCurrentComponentRootTag(final Tag newTag) {
        final Component<?, ?> component = componentsStack.peek();
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
        final Component<?, ?> component = componentsStack.peek();
        assert component != null;
        component.addEvent(eventTarget, new Event(eventTarget, eventHandler, preventDefault, modifier));
    }

    @Override
    public void addRef(final Ref ref) {
        final Component<?, ?> component = componentsStack.peek();
        assert component != null;
        component.addRef(ref, tagsStack.peek().path());
    }

    public <T, S> Component<T, S> openComponent(final Object key,
                                                final Class<T> stateOriginClass,
                                                final Function<T, CompletableFuture<? extends S>> initialStateFunction,
                                                final BiFunction<S, Path, Path> state2pathFunction,
                                                final ComponentView<S> componentView) {
        final Component<T, S> newComponent = new Component<>(key,
                                                             baseUrlPath,
                                                             new HttpStateOriginProvider<>(httpStateOriginLookup,
                                                                                           stateOriginClass,
                                                                                           initialStateFunction),
                                                             state2pathFunction,
                                                             componentView,
                                                            this,
                                                             remotePageMessagesOut);
        if (rootComponent == null) {
            rootComponent = newComponent;
        } else {
            final Component<?, ?> parentComponent = componentsStack.peek();
            parentComponent.addChild(newComponent);
        }
        componentsStack.push(newComponent);

        return newComponent;
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

    public void closeComponent() {
        componentsStack.pop();
    }

    @Override
    public ComponentRenderContext newContext(final VirtualDomPath domPath) {
        return new ComponentRenderContext(domPath,
                                        baseUrlPath,
                                        httpStateOriginLookup,
                                        remotePageMessagesOut);
    }

    @Override
    public ComponentRenderContext newContext() {
        return new ComponentRenderContext(rootDomPath,
                                          baseUrlPath,
                                          httpStateOriginLookup,
                                          remotePageMessagesOut);
    }
}


