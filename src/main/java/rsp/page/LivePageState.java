package rsp.page;

import rsp.Component;
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
    private final Component<S> rootComponent;
    private final BiFunction<String, RenderContext, RenderContext> enrich;
    private final OutMessages out;

    private S state;
    private LivePagePropertiesSnapshot snapshot;

    public LivePageState(LivePagePropertiesSnapshot snapshot,
                         QualifiedSessionId qsid,
                         StateToRouteDispatch<S> state2route,
                         Component<S> rootComponent,
                         BiFunction<String, RenderContext, RenderContext> enrich,
                         OutMessages out) {
        this.snapshot = snapshot;
        this.qsid = qsid;
        this.state2route = state2route;
        this.rootComponent = rootComponent;
        this.enrich = enrich;
        this.out = out;
    }

    @Override
    public synchronized void accept(S newState) {
        this.state = newState;

        final DomTreeRenderContext newContext = new DomTreeRenderContext();
        rootComponent.render(this).accept(enrich.apply(qsid.sessionId, newContext));

        // Calculate diff between currentContext and newContext
        final DefaultDomChangesPerformer domChangePerformer = new DefaultDomChangesPerformer();
        new Diff(Optional.of(snapshot.domRoot), newContext.root, domChangePerformer).run();
        out.modifyDom(domChangePerformer.commands);

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
        eventsToAdd.forEach(event -> {
            final Event.Target eventTarget = event.eventTarget;
            out.listenEvent(eventTarget.eventType,
                    event.preventDefault,
                    eventTarget.elementPath,
                    event.modifier);
        });

        // Browser's navigation
        final Path oldPath = snapshot.path;
        final Path newPath = state2route.stateToPath.apply(newState);
        if (!newPath.equals(oldPath)) {
            out.pushHistory(state2route.basePath.resolve(newPath).toString());
        }

        snapshot = new LivePagePropertiesSnapshot(newPath,
                                newContext.root,
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

    public synchronized LivePagePropertiesSnapshot snapshot() {
        return snapshot;
    }
}
