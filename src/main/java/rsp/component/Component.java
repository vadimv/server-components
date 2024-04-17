package rsp.component;

import rsp.dom.*;
import rsp.html.SegmentDefinition;
import rsp.page.RenderContextFactory;
import rsp.ref.Ref;
import rsp.server.RemoteOut;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

import static java.lang.System.Logger.Level.*;

/**
 * Represents a stateful component.
 * @param <S> a type for this component's state snapshot, should be an immutable class
 */
public final class Component<S> implements NewState<S> {
    private final System.Logger logger = System.getLogger(getClass().getName());

    private final List<Event> events = new ArrayList<>();
    private final Map<Ref, VirtualDomPath> refs = new HashMap<>();
    private final List<Component<?>> children = new ArrayList<>();

    private final ComponentCompositeKey key;
    private final Supplier<CompletableFuture<? extends S>> resolveStateFunction;
    private final MountCallback<S> componentDidMount;
    private final StateAppliedCallback<S> componentDidUpdate;
    private final UnmountCallback<S> componentWillUnmount;
    private final ComponentView<S> componentView;
    private final RenderContextFactory renderContextFactory;
    private final RemoteOut remotePageMessages;

    private S state;
    private Tag tag;

    public Component(final ComponentCompositeKey key,
                     final Supplier<CompletableFuture<? extends S>> resolveStateFunction,
                     final MountCallback<S> componentDidMount,
                     final ComponentView<S> componentView,
                     final StateAppliedCallback<S> componentDidUpdate,
                     final UnmountCallback<S> componentWillUnmount,
                     final RenderContextFactory renderContextFactory,
                     final RemoteOut remotePageMessages) {
        this.key = Objects.requireNonNull(key);
        this.resolveStateFunction = Objects.requireNonNull(resolveStateFunction);
        this.componentDidMount = Objects.requireNonNull(componentDidMount);
        this.componentView = Objects.requireNonNull(componentView);
        this.componentDidUpdate = Objects.requireNonNull(componentDidUpdate);
        this.componentWillUnmount = Objects.requireNonNull(componentWillUnmount);
        this.renderContextFactory = Objects.requireNonNull(renderContextFactory);
        this.remotePageMessages = Objects.requireNonNull(remotePageMessages);

        logger.log(TRACE, "New component is created with key " + this);
    }

    public ComponentPath path() {
        return key.path();
    }

    public void addChild(final Component<?> component) {
        children.add(component);
    }

    public void setRootTagIfNotSet(Tag newTag) {
      if (this.tag == null) {
            this.tag = newTag;
      }
    }

    public void render(final ComponentRenderContext renderContext) {
        final CompletableFuture<? extends S> statePromise = resolveStateFunction.get();
        statePromise.whenComplete((s, stateEx) -> {
            if (stateEx == null) {
                state = s;
                try {
                    final SegmentDefinition view = componentView.apply(state).apply(this);
                    view.render(renderContext);
                    componentDidMount.apply(key, state, this, renderContext);
                } catch (Throwable renderEx) {
                    logger.log(ERROR, "Component " + key + " rendering exception", renderEx);
                }
            } else {
                logger.log(ERROR, "Component " + key + " state exception", stateEx);
            }
        });
    }

    public S getState() {
        return state;
    }

    @Override
    public void set(final S newState) {
        apply(s -> newState);
    }

    @Override
    public void applyWhenComplete(final CompletableFuture<? extends S> newState) {
        newState.thenAccept(this::set);
    }

    @Override
    public void applyIfPresent(final Function<S, Optional<S>> stateTransformer) {
        stateTransformer.apply(state).ifPresent(this::set);
    }

    @Override
    public void apply(final UnaryOperator<S> newStateFunction) {
        if (tag == null) {
            throw new IllegalStateException("Component " + this + " root tag is not rendered");
        }
        final Tag oldTag = tag;
        tag = null;
        final Set<Event> oldEvents = new HashSet<>(recursiveEvents());
        final Set<Component<?>> oldChildren = new HashSet<>(recursiveChildren());
        final S oldState = state;
        state = newStateFunction.apply(state);
        logger.log(TRACE, () -> "Component " + this + " old state was " + oldState + " applied new state " + state);

        final ComponentRenderContext renderContext = renderContextFactory.newContext(oldTag.path());

        events.clear();
        refs.clear();
        children.clear();

        renderContext.openComponent(this);
        final SegmentDefinition view = componentView.apply(state).apply(this);
        componentDidMount.apply(key, state, this, renderContext);
        view.render(renderContext);
        renderContext.closeComponent();
        componentDidUpdate.apply(key, state, renderContext);

        tag = renderContext.rootTag();

        final RemoteOut remoteOut = remotePageMessages;
        assert remoteOut != null;

        // Calculate diff between an old and new DOM trees
        final DefaultDomChangesContext domChangePerformer = new DefaultDomChangesContext();
        new Diff(Optional.ofNullable(oldTag), renderContext.rootTag(), domChangePerformer).run();
        final Set<VirtualDomPath> elementsToRemove = domChangePerformer.elementsToRemove;
        remoteOut.modifyDom(domChangePerformer.commands);

        // Unregister events
        final List<Event> eventsToRemove = new ArrayList<>();
        final Set<Event> newEvents = new HashSet<>(recursiveEvents());
        for (Event event : oldEvents) {
            if (!newEvents.contains(event) && !elementsToRemove.contains(event.eventTarget.elementPath)) {
                eventsToRemove.add(event);
            }
        }
        for (Event event : eventsToRemove) {
            final Event.Target eventTarget = event.eventTarget;
            remoteOut.forgetEvent(eventTarget.eventType,
                                  eventTarget.elementPath);
        }

        // Register new event types on client
        final List<Event> eventsToAdd = new ArrayList<>();
        for (final Event event : newEvents) {
            if(!oldEvents.contains(event)) {
                eventsToAdd.add(event);
            }
        }
        remoteOut.listenEvents(eventsToAdd);

        // Notify unmounted child components
        final Set<Component<?>> mountedComponents = new HashSet<>(children);
        for (final Component<?> child : oldChildren) {
            if (!mountedComponents.contains(child)) {
                child.unmount();
            }
        }
    }

    public List<Component<?>> directChildren() {
        return children;
    }

    public void unmount() {
        recursiveChildren().forEach(c -> c.unmount());
        componentWillUnmount.apply(key, state);
    }

    public List<Component<?>> recursiveChildren() {
        final List<Component<?>> recursiveChildren = new ArrayList<>();
        recursiveChildren.addAll(children);
        for (final Component<?> childComponent : children) {
            recursiveChildren.addAll(childComponent.recursiveChildren());
        }
        return recursiveChildren;
    }

    public List<Event> recursiveEvents() {
        final List<Event> recursiveEvents = new ArrayList<>();
        recursiveEvents.addAll(events);
        for (final Component<?> childComponent : children) {
            recursiveEvents.addAll(childComponent.recursiveEvents());
        }
        return recursiveEvents;
    }

    public Map<Ref, VirtualDomPath> recursiveRefs() {
        final Map<Ref, VirtualDomPath> recursiveRefs = new HashMap<>(refs);
        for (Component<?> childComponent : children) {
            recursiveRefs.putAll(childComponent.recursiveRefs());
        }
        return recursiveRefs;
    }

    public void addEvent(final Event event) {
        events.add(event);
    }

    public void addRef(final Ref ref, final VirtualDomPath path) {
        refs.put(ref, path);
    }

    @Override
    public String toString() {
        return "Component{" +
                "key=" + key +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Component<?> component = (Component<?>) o;
        return key.equals(component.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}
