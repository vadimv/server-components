package rsp.component;

import rsp.dom.*;
import rsp.html.SegmentDefinition;
import rsp.page.RenderContext;
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

    private final Map<Event.Target, Event> events = new HashMap<>();
    private final Map<Ref, VirtualDomPath> refs = new HashMap<>();
    private final List<Component<?>> children = new ArrayList<>();

    private final Object key;
    private final Supplier<CompletableFuture<? extends S>> resolveStateFunction;
    private final ComponentView<S> componentView;
    private final RenderContextFactory renderContextFactory;
    private final RemoteOut remotePageMessages;
    private final Consumer<S> stateChangedListener;

    private S state;
    private Tag tag;

    public Component(final Object key,
                     final Supplier<CompletableFuture<? extends S>> resolveStateFunction,
                     final ComponentView<S> componentView,
                     final RenderContextFactory renderContextFactory,
                     final RemoteOut remotePageMessages,
                     final Consumer<S> stateChangedListener) {
        this.key = Objects.requireNonNull(key);
        this.resolveStateFunction = Objects.requireNonNull(resolveStateFunction);
        this.componentView = Objects.requireNonNull(componentView);
        this.renderContextFactory = Objects.requireNonNull(renderContextFactory);
        this.remotePageMessages = Objects.requireNonNull(remotePageMessages);
        this.stateChangedListener = Objects.requireNonNull(stateChangedListener);
        logger.log(TRACE, "New component is created with key " + key);
    }

    public void addChild(final Component<?> component) {
        children.add(component);
    }

    public void setRootTagIfNotSet(Tag newTag) {
        if (this.tag == null) {
            this.tag = newTag;
        }
    }

    public void render(final RenderContext renderContext) {
        final CompletableFuture<? extends S> statePromise = resolveStateFunction.get();
        statePromise.whenComplete((s, stateEx) -> {
            if (stateEx == null) {
                state = s;
                try {
                    final SegmentDefinition view = componentView.apply(state).apply(this);
                    view.render(renderContext);
                } catch (Throwable renderEx) {
                    logger.log(ERROR, "Component " + key + " rendering exception", renderEx);
                }
            } else {
                logger.log(ERROR, "Component " + key + " state exception", stateEx);
            }
        });
    }

    public void resolveState() {
        applyWhenComplete(resolveStateFunction.get());
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
        final Tag oldTag = tag;
        final Map<Event.Target, Event> oldEvents = oldTag != null ?
                                              new HashMap<>(recursiveEvents()) :
                                              Map.of();
        final S oldState = state;
        state = newStateFunction.apply(state);
        logger.log(TRACE, () -> "Component's " + key + " old state was " + oldState + " applied new state " + state);

        final ComponentRenderContext renderContext = oldTag != null ?
                                        renderContextFactory.newContext(oldTag.path()) :
                                        renderContextFactory.newContext();
        events.clear();
        refs.clear();
        children.clear();

        renderContext.openComponent(this);
        final SegmentDefinition view = componentView.apply(state).apply(this);
        view.render(renderContext);
        renderContext.closeComponent();

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
        final Collection<Event> newEvents = recursiveEvents().values();
        for (Event event : oldEvents.values()) {
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
        for (Event event : newEvents) {
            if(!oldEvents.values().contains(event)) {
                eventsToAdd.add(event);
            }
        }
        remoteOut.listenEvents(eventsToAdd);

        stateChangedListener.accept(state);

        // Unmount obsolete children components

    }

    public Map<Event.Target, Event> recursiveEvents() {
        final Map<Event.Target, Event> recursiveEvents = new HashMap<>(events);
        for (Component<?> childComponent : children) {
            recursiveEvents.putAll(childComponent.recursiveEvents());
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

    public void addEvent(final Event.Target eventTarget, final Event event) {
        events.put(eventTarget, event);
    }

    public void addRef(final Ref ref, final VirtualDomPath path) {
        refs.put(ref, path);
    }
}
