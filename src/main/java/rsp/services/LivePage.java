package rsp.services;

import rsp.*;
import rsp.dom.*;
import rsp.server.HttpRequest;
import rsp.server.InMessages;
import rsp.server.OutMessages;
import rsp.state.MutableState;
import rsp.state.UseState;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LivePage<S> implements InMessages {
    private final AtomicInteger descriptorsCounter = new AtomicInteger();
    private final Map<Integer, CompletableFuture<String>> registeredEventHandlers = new ConcurrentHashMap<>();
    private final HttpRequest handshakeRequest;
    private final QualifiedSessionId qsid;
    private final Function<HttpRequest, CompletableFuture<S>> routing;
    private final BiFunction<String, S, String> state2route;
    private final Map<QualifiedSessionId, Page<S>> pagesStorage;
    private final UseState<S> stateHandler;
    private final UseState<Snapshot> currentPageSnapshot;
    private final OutMessages out;

    public LivePage(HttpRequest handshakeRequest,
                    QualifiedSessionId qsid,
                    Function<HttpRequest, CompletableFuture<S>> routing,
                    BiFunction<String, S, String> state2route,
                    Map<QualifiedSessionId, Page<S>> pagesStorage,
                    UseState<S> stateHandler,
                    UseState<Snapshot> current,
                    OutMessages out) {
        this.handshakeRequest = handshakeRequest;
        this.qsid = qsid;
        this.routing = routing;
        this.state2route = state2route;
        this.pagesStorage = pagesStorage;
        this.stateHandler = stateHandler;
        this.currentPageSnapshot = current;
        this.out = out;
    }

    public static <S> LivePage<S> of(HttpRequest handshakeRequest,
                                       QualifiedSessionId qsid,
                                       Function<HttpRequest, CompletableFuture<S>> routing,
                                       BiFunction<String, S, String> state2route,
                                       Map<QualifiedSessionId, Page<S>> pagesStorage,
                                       Component<S> documentDefinition,
                                       BiFunction<String, RenderContext<S>, RenderContext<S>> enrich,
                                       OutMessages out) {


        final UseState<Snapshot> current = new MutableState<>(new Snapshot(Optional.empty(),
                                                                           new HashMap<>(),
                                                                           new HashMap<>()));
        final UseState<S> useState = new UseState<S>() {
            private volatile S state = null;

            @Override
            public void accept(S newState) {
                    state = newState;

                    final DomTreeRenderContext<S> newContext = new DomTreeRenderContext<>();
                    documentDefinition.materialize(this).accept(enrich.apply(qsid.sessionId, newContext));

                    // calculate diff between currentContext and newContext
                    final var currentRoot = current.get().domRoot;
                    final var remoteChangePerformer = new RemoteDomChangesPerformer();
                    new Diff(currentRoot, newContext.root, remoteChangePerformer).run();

                    out.modifyDom(remoteChangePerformer.commands);

                    // Register new event types on client
                    final Set<Event> newEvents = new HashSet<>();
                    final Set<Event> oldEvents = current.get().events.values().stream().collect(Collectors.toSet());
                    for(Event event : newContext.events.values()) {
                        if(!oldEvents.contains(event)) {
                            newEvents.add(event);
                        }
                    }
                    newEvents.stream()
                             .forEach(event -> {
                                 final Event.Target eventTarget = event.eventTarget;
                                 out.listenEvent(eventTarget.eventType,
                                                 eventTarget.eventType.equals("submit"),
                                                 eventTarget.elementPath,
                                                 event.modifier);
                            });

                    current.accept(new Snapshot(Optional.of(newContext.root), newContext.events, newContext.refs));
            }

            @Override
            public S get() {
                if (state != null) {
                    return state;
                } else {
                    throw new IllegalStateException("Live page state not initialized.");
                }

            }
        };

        return new LivePage<>(handshakeRequest,
                              qsid,
                              routing,
                              state2route,
                              pagesStorage,
                              useState,
                              current,
                              out);
    }

    public void start() {
        final Page<S> page = pagesStorage.get(qsid);
        if (page == null) {
            routing.apply(handshakeRequest).thenAccept(state -> {
                stateHandler.accept(state);
                out.setRenderNum(0);
            });
        } else {
            final var s = currentPageSnapshot.get();
            pagesStorage.remove(qsid);
            currentPageSnapshot.accept(new Snapshot(Optional.of(page.domRoot), s.events, s.refs));
            stateHandler.accept(page.initialState);
            out.setRenderNum(0);
        }
    }

    @Override
    public void extractProperty(int descriptorId, String value) {
        System.out.println("extractProperty:" + descriptorId + " value=" + value);
        final var cf = registeredEventHandlers.get(descriptorId);
        if (cf != null) {
            cf.complete(value);
            registeredEventHandlers.remove(descriptorId);
        }
    }

    @Override
    public void domEvent(int renderNumber, Path path, String eventType) {
        Path eventElementPath = path;
        if (path.equals(Path.WINDOW)) {
            final Event event = currentPageSnapshot.get().events.get(new Event.Target(eventType, eventElementPath));
            final EventContext eventContext = new EventContext(() -> descriptorsCounter.incrementAndGet(),
                                                                     registeredEventHandlers,
                                                                     ref -> currentPageSnapshot.get().refs.get(ref),
                                                                     out);
            event.eventHandler.accept(eventContext);
            return;
        }

        while(eventElementPath.level() > 1) {
            final Event event = currentPageSnapshot.get().events.get(new Event.Target(eventType, eventElementPath));
            if (event != null && event.eventTarget.eventType.equals(eventType)) {
               final EventContext eventContext = new EventContext(() -> descriptorsCounter.incrementAndGet(),
                                                                        registeredEventHandlers,
                                                                        ref -> currentPageSnapshot.get().refs.get(ref),
                                                                        out);
               event.eventHandler.accept(eventContext);
               break;
            } else if (eventElementPath.level() > 1) {
                eventElementPath = eventElementPath.parent().get();
            } else {
                // TODO log illegal state 'a DOM event handler not found'
            }
        }
    }

    private static class Snapshot {
        public final Optional<Tag> domRoot;
        public final Map<Event.Target, Event> events;
        public final Map<Ref, Path> refs;


        public Snapshot(Optional<Tag> domRoot,
                        Map<Event.Target, Event> events,
                        Map<Ref, Path> refs) {
            this.domRoot = domRoot;
            this.events = events;
            this.refs = refs;
        }
    }
}
