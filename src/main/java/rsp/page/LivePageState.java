package rsp.page;

import rsp.ComponentStateFunction;
import rsp.dom.*;
import rsp.html.DocumentPartDefinition;
import rsp.server.OutMessages;
import rsp.server.Path;

import java.util.*;
import java.util.function.BiFunction;

public class LivePageState<S> implements Runnable {
    private final QualifiedSessionId qsid;
    private final StateToRouteDispatch<S> state2route;
    private final BiFunction<String, PageRenderContext, PageRenderContext> enrich;
    private final OutMessages out;

    private LivePageSnapshot<S> snapshot;

    public LivePageState(QualifiedSessionId qsid,
                         LivePageSnapshot<S> snapshot,
                         StateToRouteDispatch<S> state2route,
                         BiFunction<String, PageRenderContext, PageRenderContext> enrich,
                         OutMessages out) {
        this.snapshot = snapshot;
        this.qsid = qsid;
        this.state2route = state2route;
        this.enrich = enrich;
        this.out = out;
    }

    public void setSnapshot(LivePageSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot);
    }

    public synchronized void run() {
        final StateNotificationListener componentsStateNotificationListener = new StateNotificationListener();
        final DomTreePageRenderContext newContext = new DomTreePageRenderContext(componentsStateNotificationListener);
        final PageRenderContext enrichedNewContext = enrich.apply(qsid.sessionId, newContext);
        snapshot.rootComponent.render(enrichedNewContext);

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

        // Browser's navigation
        final Path oldPath = snapshot.path;
        final Path newPath = state2route.stateToPath.apply(snapshot.rootComponent.state, oldPath);
        if (!newPath.equals(oldPath)) {
            out.pushHistory(state2route.basePath.resolve(newPath).toString());
        }
        componentsStateNotificationListener.setListener(this);
        snapshot = new LivePageSnapshot(snapshot.rootComponent,
                                        componentsStateNotificationListener,
                                        newPath,
                                        newContext.root(),
                                        Collections.unmodifiableMap(newContext.events),
                                        Collections.unmodifiableMap(newContext.refs));
    }

/*
    @Override
    public synchronized S get() {
        return snapshot.state;
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
*/

    public synchronized LivePageSnapshot<S> snapshot() {
        return snapshot;
    }
}
