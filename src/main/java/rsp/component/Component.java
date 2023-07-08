package rsp.component;

import rsp.dom.Event;
import rsp.dom.Tag;
import rsp.dom.VirtualDomPath;
import rsp.html.SegmentDefinition;
import rsp.page.LivePageSession;
import rsp.page.RenderContext;
import rsp.ref.Ref;
import rsp.server.RemoteOut;
import rsp.server.Path;
import rsp.stateview.ComponentView;
import rsp.stateview.NewState;
import rsp.util.Lookup;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Component<T, S> implements NewState<S> {

    private final Map<Event.Target, Event> events = new HashMap<>();
    private final Map<Ref, VirtualDomPath> refs = new HashMap<>();
    private final List<Component<?, ?>> children = new ArrayList<>();

    private final Lookup stateOriginLookup;
    private final Class<T> stateOriginClass;
    private final Function<T, CompletableFuture<? extends S>> resolveStateFunction;
    private final BiFunction<S, Path, Path> state2pathFunction;
    private final ComponentView<S> componentView;
    private final RenderContext parentRenderContext;
    private final AtomicReference<LivePageSession> livePageContext;

    private S state;
    private Tag tag;

    public Component(final Lookup stateOriginLookup,
                     final Class<T> stateOriginClass,
                     final Function<T, CompletableFuture<? extends S>> resolveStateFunction,
                     final BiFunction<S, Path, Path> state2pathFunction,
                     final ComponentView<S> componentView,
                     final RenderContext parentRenderContext,
                     final AtomicReference<LivePageSession> livePageSupplier) {
        this.stateOriginLookup = stateOriginLookup;
        this.stateOriginClass = Objects.requireNonNull(stateOriginClass);
        this.resolveStateFunction = Objects.requireNonNull(resolveStateFunction);
        this.state2pathFunction = Objects.requireNonNull(state2pathFunction);
        this.componentView = Objects.requireNonNull(componentView);
        this.parentRenderContext = Objects.requireNonNull(parentRenderContext);
        this.livePageContext = Objects.requireNonNull(livePageSupplier);
    }

    public void addChild(final Component<?, ?> component) {
        children.add(component);
    }

    public void setRootTagIfNotSet(Tag newTag) {
        if (this.tag == null) {
            this.tag = newTag;
        }
    }

    public S resolveState() {
        final T stateOrigin = stateOriginLookup.lookup(stateOriginClass);
        final CompletableFuture<? extends S> initialStateCompletableFuture = resolveStateFunction.apply(stateOrigin);
        try {
            return initialStateCompletableFuture.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void resolveAndSet() {
        set(resolveState());
    }

    public S getState() {
        return state;
    }

    @Override
    public void set(final S newState) {
        apply(s -> newState);
    }

    @Override
    public void applyWhenComplete(final CompletableFuture<S> newState) {
        newState.thenAccept(this::set);
    }

    @Override
    public void applyIfPresent(final Function<S, Optional<S>> stateTransformer) {
        stateTransformer.apply(state).ifPresent(this::set);
    }

    @Override
    public void apply(final Function<S, S> newStateFunction) {
        final LivePageSession livePage = livePageContext.get();
        synchronized (livePage) {
            final Tag oldTag = tag;
            final Map<Event.Target, Event> oldEvents = oldTag != null ?
                                                  new HashMap<>(recursiveEvents()) :
                                                  Map.of();
            state = newStateFunction.apply(state);
            final RenderContext renderContext = oldTag != null ?
                                                   parentRenderContext.newContext(oldTag.path) :
                                                   parentRenderContext.newContext();
            events.clear();
            refs.clear();
            children.clear();

            renderContext.openComponent(this);
            final SegmentDefinition view = componentView.apply(state).apply(this);
            view.render(renderContext);
            renderContext.closeComponent();

            tag = renderContext.rootTag();
            final Set<VirtualDomPath> elementsToRemove = livePage.updateDom(Optional.ofNullable(oldTag), renderContext.rootTag());
            livePage.updateEvents(new HashSet<>(oldEvents.values()), new HashSet<>(recursiveEvents().values()), elementsToRemove);

            // Browser's navigation
            livePage.applyToPath(path -> state2pathFunction.apply(state, path));
        }
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
        remoteOut.listenEvents(events.values().stream().collect(Collectors.toList()));
        children.forEach(childComponent -> childComponent.listenEvents(remoteOut));
    }

    public void addEvent(Event.Target eventTarget, Event event) {
        events.put(eventTarget, event);
    }

    public void addRef(Ref ref, VirtualDomPath path) {
        refs.put(ref, path);
    }
}
