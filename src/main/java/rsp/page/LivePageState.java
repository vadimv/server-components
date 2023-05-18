package rsp.page;

import rsp.ComponentStateFunction;
import rsp.dom.*;
import rsp.server.OutMessages;
import rsp.server.Path;
import rsp.state.UseState;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public class LivePageState<S> implements UseState<S> {
    private final QualifiedSessionId qsid;
    private final StateToRouteDispatch<S> state2route;
    private final ComponentStateFunction<S> rootComponentStateFunction;
    private final BiFunction<String, PageRenderContext, PageRenderContext> enrich;
    private final OutMessages out;

    private S state;
    private LivePageSnapshot snapshot;

    public LivePageState(
                         LivePageSnapshot snapshot,
                         QualifiedSessionId qsid,
                         StateToRouteDispatch<S> state2route,
                         ComponentStateFunction<S> rootComponentStateFunction,
                         BiFunction<String, PageRenderContext, PageRenderContext> enrich,
                         OutMessages out) {
        this.snapshot = snapshot;
        this.qsid = qsid;
        this.state2route = state2route;
        this.rootComponentStateFunction = rootComponentStateFunction;
        this.enrich = enrich;
        this.out = out;
    }

    @Override
    public synchronized void accept(S newState) {
        this.state = newState;

        final DomTreePageRenderContext newContext = new DomTreePageRenderContext();
        rootComponentStateFunction.apply(this.state).render(enrich.apply(qsid.sessionId, newContext));

        // Calculate diff between currentContext and newContext
        final DefaultDomChangesPerformer domChangePerformer = new DefaultDomChangesPerformer();
        new Diff(Optional.of(snapshot.domRoot), newContext.root(), domChangePerformer).run();
        if ( domChangePerformer.commands.size() > 0) {
            out.modifyDom(domChangePerformer.commands);
        }

        // Events
        final Set<Event> oldEvents = new HashSet<>(snapshot.events.values());
        final Set<Event> newEvents = new HashSet<>(newContext.events.values());
        // Unregister events
        final Set<Event> eventsToRemove = new HashSet<>();
        for(Event event : oldEvents) {
            if(!newEvents.contains(event) && !domChangePerformer.elementsToRemove.contains(event.eventTarget.elementPath)) {
                eventsToRemove.add(event);
            }
        }
        eventsToRemove.forEach(event -> {
            final Event.Target eventTarget = event.eventTarget;
            out.forgetEvent(eventTarget.eventType,
                            eventTarget.elementPath);
        });

        // Register new event types on client
        final Set<Event> eventsToAdd = new HashSet<>();
        for(Event event : newEvents) {
            if(!oldEvents.contains(event)) {
                eventsToAdd.add(event);
            }
        }
        out.listenEvents(new ArrayList<>(eventsToAdd));
/*        eventsToAdd.forEach(event -> {
            final Event.Target eventTarget = event.eventTarget;
            out.listenEvent(eventTarget.eventType,
                    event.preventDefault,
                    eventTarget.elementPath,
                    event.modifier);
        });*/

        // Browser's navigation
        final Path oldPath = snapshot.path;
        final Path newPath = state2route.stateToPath.apply(newState, oldPath);
        if (!newPath.equals(oldPath)) {
            out.pushHistory(state2route.basePath.resolve(newPath).toString());
        }

        snapshot = new LivePageSnapshot(newState,
                                        newPath,
                                        newContext.root(),
                                        Collections.unmodifiableMap(newContext.events),
                                        Collections.unmodifiableMap(newContext.refs));
    }

    @Override
    public synchronized S get() {
        return state;
    }

    @Override
    public synchronized void accept(CompletableFuture<S> completableFuture) {
        completableFuture.thenAccept(s -> accept(s));
    }

    @Override
    public synchronized void accept(Function<S, S> function) {
        accept(function.apply(get()));
    }

    @Override
    public synchronized void acceptOptional(Function<S, Optional<S>> function) {
        function.apply(state).ifPresent(s -> accept(s));
    }

    public synchronized LivePageSnapshot<S> snapshot() {
        return snapshot;
    }
}
