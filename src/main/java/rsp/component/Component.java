package rsp.component;

import rsp.dom.*;
import rsp.html.SegmentDefinition;
import rsp.page.RenderContext;
import rsp.page.RenderContextFactory;
import rsp.ref.Ref;
import rsp.server.RemoteOut;
import rsp.util.TriConsumer;

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

    private final Object key;
    private final Supplier<CompletableFuture<? extends S>> resolveStateFunction;
    private final ComponentView<S> componentView;
    private final RenderContextFactory renderContextFactory;
    private final RemoteOut remotePageMessages;
    private final Consumer<S> stateChangedListener;
    private final TriConsumer<S, NewState<S>, RenderContext> beforeRenderHook;

    private S state;
    private Tag tag;

    public Component(final Object key,
                     final Supplier<CompletableFuture<? extends S>> resolveStateFunction,
                     final ComponentView<S> componentView,
                     final RenderContextFactory renderContextFactory,
                     final RemoteOut remotePageMessages,
                     final Consumer<S> stateChangedListener,
                     final TriConsumer<S, NewState<S>, RenderContext> beforeRenderHook) {
        this.key = Objects.requireNonNull(key);
        this.resolveStateFunction = Objects.requireNonNull(resolveStateFunction);
        this.componentView = Objects.requireNonNull(componentView);
        this.renderContextFactory = Objects.requireNonNull(renderContextFactory);
        this.remotePageMessages = Objects.requireNonNull(remotePageMessages);
        this.stateChangedListener = Objects.requireNonNull(stateChangedListener);
        this.beforeRenderHook = Objects.requireNonNull(beforeRenderHook);
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
                    beforeRenderHook.accept(state, this, renderContext);
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
        final Set<Event> oldEvents = new HashSet<>(recursiveEvents());
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
        beforeRenderHook.accept(state, this, renderContext);
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

        stateChangedListener.accept(state);

        // Unmount obsolete children components

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
}
