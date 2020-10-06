package rsp.services;

import rsp.*;
import rsp.dom.*;
import rsp.server.InMessages;
import rsp.server.OutMessages;
import rsp.state.MutableState;
import rsp.state.UseState;
import rsp.util.Tuple2;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class LivePage<S> implements InMessages {
    private final AtomicInteger descriptorsCounter = new AtomicInteger();
    private final Map<Integer, CompletableFuture<String>> registeredEventHandlers = new ConcurrentHashMap<>();

    private final UseState<S> useState;
    private final UseState<Snapshot> current;
    private final OutMessages out;

    public LivePage(UseState<S> useState,
                    UseState<Snapshot> current,
                    OutMessages out) {
        this.useState = useState;
        this.current = current;
        this.out = out;
    }

    public static <S> Optional<LivePage<S>> of(Map<QualifiedSessionId, Page<S>> pagesStorage,
                                               Map<String, String> pathParameters,
                                               BiFunction<String, RenderContext<S>, RenderContext<S>> contextEnrich,
                                               OutMessages out) {
        final QualifiedSessionId qsid = new QualifiedSessionId(pathParameters.get("pid"), pathParameters.get("sid"));
        final Page<S> page = pagesStorage.get(qsid);
        if (page == null) {
            // a connection after server's restart
            out.evalJs(0, "location.reload()");
            return Optional.empty();
        }

        pagesStorage.remove(qsid);

        final UseState<Snapshot> current = new MutableState<>(new Snapshot(page.domRoot,
                                                                           new HashMap<>(),
                                                                           new HashMap<>()));
        final UseState<S> useState = new UseState<S>() {
            private volatile S state = page.initialState;
            @Override
            public void accept(S newState) {
                final String newRoute = page.state2route.apply(page.path, newState);
                if (newRoute.equals(page.path)) {
                    state = newState;

                    final DomTreeRenderContext<S> newContext = new DomTreeRenderContext<>();
                    page.rootComponent.materialize(this).accept(contextEnrich.apply(qsid.sessionId, newContext));

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

                    current.accept(new Snapshot(newContext.root, newContext.events, newContext.refs));
                } else {
                    // send new URL/route command
                    System.out.println("New route: " + newRoute);
                }
            }

            @Override
            public S get() {
                return state;
            }
        };
        useState.accept(page.initialState);

        out.setRenderNum(0);//TODO

        return Optional.of(new LivePage<>(useState,
                                          current,
                                          out));
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
            final Event event = current.get().events.get(new Event.Target(eventType, eventElementPath));
            final EventContext eventContext = new EventContext(() -> descriptorsCounter.incrementAndGet(),
                                                                     registeredEventHandlers,
                                                                     ref -> current.get().refs.get(ref),
                                                                     out);
            event.eventHandler.accept(eventContext);
            return;
        }

        while(eventElementPath.level() > 1) {
            final Event event = current.get().events.get(new Event.Target(eventType, eventElementPath));
            if (event != null && event.eventTarget.eventType.equals(eventType)) {
               final EventContext eventContext = new EventContext(() -> descriptorsCounter.incrementAndGet(),
                                                                        registeredEventHandlers,
                                                                        ref -> current.get().refs.get(ref),
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
        public final Tag domRoot;
        public final Map<Event.Target, Event> events;
        public final Map<Ref, Path> refs;


        public Snapshot(Tag domRoot,
                        Map<Event.Target, Event> events,
                        Map<Ref, Path> refs) {
            this.domRoot = domRoot;
            this.events = events;
            this.refs = refs;
        }
    }
}
