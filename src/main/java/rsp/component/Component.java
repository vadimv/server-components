package rsp.component;

import rsp.dom.*;
import rsp.html.SegmentDefinition;
import rsp.page.RenderContext;
import rsp.page.RenderContextFactory;
import rsp.ref.Ref;
import rsp.server.RemoteOut;
import rsp.server.Path;
import rsp.server.http.HttpStateOriginProvider;
import rsp.server.http.RelativeUrl;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Represents a stateful component.
 * @param <T> a type to be resolved to an initial state
 * @param <S> a type for this component's state snapshot, should be an immutable class
 */
public final class Component<T, S> implements NewState<S> {

    private final Map<Event.Target, Event> events = new HashMap<>();
    private final Map<Ref, VirtualDomPath> refs = new HashMap<>();
    private final List<Component<?, ?>> children = new ArrayList<>();

    private final Object key;
    private final Path basePath;
    private final HttpStateOriginProvider<T, S> httpStateOriginProvider;
    private final BiFunction<S, Path, Path> state2pathFunction;
    private final ComponentView<S> componentView;
    private final RenderContextFactory renderContextFactory;
    private final AtomicReference<RemoteOut> remoteOutReference;

    private S state;
    private Tag tag;

    public Component(final Object key,
                     final Path basePath,
                     final HttpStateOriginProvider<T, S> httpStateOriginProvider,
                     final BiFunction<S, Path, Path> state2pathFunction,
                     final ComponentView<S> componentView,
                     final RenderContextFactory renderContextFactory,
                     final AtomicReference<RemoteOut> remoteOutReference) {
        this.key = Objects.requireNonNull(key);
        this.basePath = Objects.requireNonNull(basePath);
        this.httpStateOriginProvider = Objects.requireNonNull(httpStateOriginProvider);
        this.state2pathFunction = Objects.requireNonNull(state2pathFunction);
        this.componentView = Objects.requireNonNull(componentView);
        this.renderContextFactory = Objects.requireNonNull(renderContextFactory);
        this.remoteOutReference = Objects.requireNonNull(remoteOutReference);
    }

    public void addChild(final Component<?, ?> component) {
        children.add(component);
    }

    public void setRootTagIfNotSet(Tag newTag) {
        if (this.tag == null) {
            this.tag = newTag;
        }
    }

    public void render(final RenderContext renderContext) {
        final CompletableFuture<? extends S> statePromice = httpStateOriginProvider.getStatePromise();
        statePromice.whenComplete((s, ex) -> { // TODO handle an exception
            state = s;
            final SegmentDefinition view = componentView.apply(state).apply(this);
            view.render(renderContext);
        });
    }

    public void resolveState() {
        applyWhenComplete( httpStateOriginProvider.getStatePromise());
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
        state = newStateFunction.apply(state);
        final RenderContext renderContext = oldTag != null ?
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

        final RemoteOut remoteOut = remoteOutReference.get();
        assert  remoteOut != null;

        // Calculate diff between an old and new DOM trees
        final DefaultDomChangesContext domChangePerformer = new DefaultDomChangesContext();
        new Diff(Optional.ofNullable(oldTag), renderContext.rootTag(), domChangePerformer).run();
        final Set<VirtualDomPath> elementsToRemove = domChangePerformer.elementsToRemove;
        remoteOut.modifyDom(domChangePerformer.commands);

        // Unregister events
        final List<Event> eventsToRemove = new ArrayList<>();
        final Collection<Event> newEvents = recursiveEvents().values();
        for(Event event : oldEvents.values()) {
            if(!newEvents.contains(event) && !elementsToRemove.contains(event.eventTarget.elementPath)) {
                eventsToRemove.add(event);
            }
        }
        for(Event event : eventsToRemove) {
            final Event.Target eventTarget = event.eventTarget;
            remoteOut.forgetEvent(eventTarget.eventType,
                                  eventTarget.elementPath);
        }

        // Register new event types on client
        final List<Event> eventsToAdd = new ArrayList<>();
        for(Event event : newEvents) {
            if(!oldEvents.values().contains(event)) {
                eventsToAdd.add(event);
            }
        }
        remoteOut.listenEvents(eventsToAdd);

        // Update browser's navigation
        final RelativeUrl oldRelativeUrl = httpStateOriginProvider.relativeUrl();
        final Path oldPath = oldRelativeUrl.path();
        final Path newPath = state2pathFunction.apply(state, oldPath);
        if (!newPath.equals(oldPath)) {
            httpStateOriginProvider.setRelativeUrl(new RelativeUrl(newPath, oldRelativeUrl.query(), oldRelativeUrl.fragment()));
            remoteOut.pushHistory(basePath.resolve(newPath).toString());
        }

        // Unmount obsolete children components

    }

    public Map<Event.Target, Event> recursiveEvents() {
        final Map<Event.Target, Event> recursiveEvents = new HashMap<>(events);
        for (Component<?, ?> childComponent : children) {
            recursiveEvents.putAll(childComponent.recursiveEvents());
        }
        return recursiveEvents;
    }

    public Map<Ref, VirtualDomPath> recursiveRefs() {
        final Map<Ref, VirtualDomPath> recursiveRefs = new HashMap<>(refs);
        for (Component<?, ?> childComponent : children) {
            recursiveRefs.putAll(childComponent.recursiveRefs());
        }
        return recursiveRefs;
    }

    public void listenEvents(final RemoteOut remoteOut) {
        remoteOut.listenEvents(events.values().stream().toList());
        children.forEach(childComponent -> childComponent.listenEvents(remoteOut));
    }

    public void addEvent(Event.Target eventTarget, Event event) {
        events.put(eventTarget, event);
    }

    public void addRef(Ref ref, VirtualDomPath path) {
        refs.put(ref, path);
    }
}
