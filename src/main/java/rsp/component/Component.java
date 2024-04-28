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
public class Component<S> implements StateUpdate<S> {
    private final System.Logger logger = System.getLogger(getClass().getName());

    private final List<Event> events = new ArrayList<>();
    private final Map<Ref, VirtualDomPath> refs = new HashMap<>();
    private final List<Component<?>> children = new ArrayList<>();

    private final ComponentCompositeKey key;
    private final Supplier<CompletableFuture<? extends S>> resolveStateFunction;
    private final ComponentMountedCallback<S> componentMounted;
    private final ComponentUpdatedCallback<S> componentUpdated;
    private final ComponentUnmountedCallback<S> componentUnmounted;
    private final ComponentView<S> componentView;
    private final RenderContextFactory renderContextFactory;

    protected final RemoteOut remotePageMessages;

    private S state;
    private Tag tag;
    private VirtualDomPath domPath;

    public Component(final ComponentCompositeKey key,
                     final Supplier<CompletableFuture<? extends S>> resolveStateSupplier,
                     final ComponentView<S> componentView,
                     final ComponentCallbacks<S> componentCallbacks,
                     final RenderContextFactory renderContextFactory,
                     final RemoteOut remotePageMessages) {
        this.key = Objects.requireNonNull(key);
        this.resolveStateFunction = Objects.requireNonNull(resolveStateSupplier);
        this.componentMounted = Objects.requireNonNull(componentCallbacks.componentMountedCallback());
        this.componentView = Objects.requireNonNull(componentView);
        this.componentUpdated = Objects.requireNonNull(componentCallbacks.componentUpdatedCallback());
        this.componentUnmounted = Objects.requireNonNull(componentCallbacks.componentUnmountedCallback());
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

    public void setRootTagIfNotSet(VirtualDomPath domPath, Tag newTag) {
      if (this.tag == null) {
            this.domPath = domPath;
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
                    initiallyRendered(key, state, this);
                    componentMounted.apply(key, state, this);
                } catch (Throwable renderEx) {
                    logger.log(ERROR, "Component " + this + " rendering exception", renderEx);
                }
            } else {
                logger.log(ERROR, "Component " + this + " state exception", stateEx);
            }
        });
    }

    public S getState() {
        return state;
    }

    @Override
    public void setState(final S newState) {
        applyStateTransformation(s -> newState);
    }

    @Override
    public void setStateWhenComplete(final CompletableFuture<? extends S> newState) {
        newState.thenAccept(this::setState);
    }

    @Override
    public void applyStateTransformationIfPresent(final Function<S, Optional<S>> stateTransformer) {
        stateTransformer.apply(state).ifPresent(this::setState);
    }

    @Override
    public void applyStateTransformation(final UnaryOperator<S> newStateFunction) {
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

        final ComponentRenderContext renderContext = renderContextFactory.newContext(domPath);

        events.clear();
        refs.clear();
        children.clear();

        renderContext.openComponent(this);
        final SegmentDefinition view = componentView.apply(state).apply(this);
        view.render(renderContext);
        renderContext.closeComponent();

        updateRendered(key, oldState, state, this);

        tag = renderContext.rootTag();

        final RemoteOut remoteOut = remotePageMessages;
        assert remoteOut != null;

        // Calculate diff between an old and new DOM trees
        final DefaultDomChangesContext domChangePerformer = new DefaultDomChangesContext();
        Diff.diff(oldTag, renderContext.rootTag(), domPath, domChangePerformer);
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
        componentUpdated.apply(key, oldState, state, this);
    }

    protected void initiallyRendered(ComponentCompositeKey key, S state, StateUpdate<S> stateUpdate) {}

    protected void updateRendered(ComponentCompositeKey key, S oldState, S state, StateUpdate<S> stateUpdate) {}

    protected void unmounted(ComponentCompositeKey key, S oldState) {}

    public List<Component<?>> directChildren() {
        return children;
    }

    public void unmount() {
        recursiveChildren().forEach(c -> c.unmount());
        unmounted(key, state);
        componentUnmounted.apply(key, state);
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
