package rsp.services;

import rsp.*;
import rsp.dom.*;
import rsp.server.HttpRequest;
import rsp.server.InMessages;
import rsp.server.OutMessages;
import rsp.state.MutableState;
import rsp.state.UseState;
import rsp.util.Log;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LivePage<S> implements InMessages, Schedule {
    public static final String POST_START_EVENT_TYPE = "page-start";
    public static final String POST_SHUTDOWN_EVENT_TYPE = "page-shutdown";

    private final AtomicInteger descriptorsCounter = new AtomicInteger();
    private final Map<Integer, CompletableFuture<String>> registeredEventHandlers = new ConcurrentHashMap<>();
    private final HttpRequest handshakeRequest;
    private final QualifiedSessionId qsid;
    private final Function<HttpRequest, CompletableFuture<S>> routing;
    private final BiFunction<String, S, String> state2route;
    private final Map<QualifiedSessionId, PageRendering.RenderedPage<S>> renderedPages;
    private final UseState<S> stateHandler;
    private final UseState<Snapshot> currentPageSnapshot;
    private final ScheduledExecutorService scheduledExecutorService;
    private final OutMessages out;
    private final Log.Reporting log;

    public LivePage(HttpRequest handshakeRequest,
                    QualifiedSessionId qsid,
                    Function<HttpRequest, CompletableFuture<S>> routing,
                    BiFunction<String, S, String> state2route,
                    Map<QualifiedSessionId, PageRendering.RenderedPage<S>> renderedPages,
                    UseState<S> stateHandler,
                    UseState<Snapshot> current,
                    ScheduledExecutorService scheduledExecutorService,
                    OutMessages out,
                    Log.Reporting log) {
        this.handshakeRequest = handshakeRequest;
        this.qsid = qsid;
        this.routing = routing;
        this.state2route = state2route;
        this.renderedPages = renderedPages;
        this.stateHandler = stateHandler;
        this.currentPageSnapshot = current;
        this.scheduledExecutorService = scheduledExecutorService;
        this.out = out;
        this.log = log;
    }

    public static <S> LivePage<S> of(HttpRequest handshakeRequest,
                                     QualifiedSessionId qsid,
                                     Function<HttpRequest, CompletableFuture<S>> routing,
                                     BiFunction<String, S, String> state2route,
                                     Map<QualifiedSessionId, PageRendering.RenderedPage<S>> renderedPages,
                                     Component<S> documentDefinition,
                                     BiFunction<String, RenderContext, RenderContext> enrich,
                                     ScheduledExecutorService scheduler,
                                     OutMessages out,
                                     Log.Reporting log) {
        final UseState<Snapshot> current = new MutableState<>(new Snapshot(Optional.empty(),
                                                                           new HashMap<>(),
                                                                           new HashMap<>()));
        final UseState<S> useState = new MutableState<S>(null).addListener(((newState, self) -> {
            final DomTreeRenderContext newContext = new DomTreeRenderContext();
            documentDefinition.render(self).accept(enrich.apply(qsid.sessionId, newContext));

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
        }));

        return new LivePage<>(handshakeRequest,
                              qsid,
                              routing,
                              state2route,
                              renderedPages,
                              useState,
                              current,
                              scheduler,
                              out,
                              log);
    }

    public void start() {
        log.info(l -> l.log("Live Page start: " + this));
        synchronized (this) {
            final PageRendering.RenderedPage<S> page = renderedPages.get(qsid);
            if (page == null) {
                routing.apply(handshakeRequest).thenAccept(state -> {
                    stateHandler.accept(state);
                    out.setRenderNum(0);
                });
            } else {
                renderedPages.remove(qsid);
                final var s = currentPageSnapshot.get();
                currentPageSnapshot.accept(new Snapshot(Optional.of(page.domRoot), s.events, s.refs));
                stateHandler.accept(page.state);
                out.setRenderNum(0);
            }

            // Invoke this page's post start events
            currentPageSnapshot.get().events.values().forEach(event -> { // TODO should these events to be ordered by its elements paths?
                if (POST_START_EVENT_TYPE.equals(event.eventTarget.eventType)) {
                    final EventContext eventContext = new EventContext(() -> descriptorsCounter.incrementAndGet(),
                                                                        registeredEventHandlers,
                                                                        ref -> currentPageSnapshot.get().refs.get(ref),
                                                                        s -> Optional.empty(),
                                                                        this,
                                                                        out);
                    event.eventHandler.accept(eventContext);
                }
            });
        }
    }

    public void shutdown() {
        log.info(l -> l.log("Live Page shutdown: " + this));
        // Invoke this page's post shutdown events
        currentPageSnapshot.get().events.values().forEach(event -> { // TODO should these events to be ordered by its elements paths?
            if (POST_SHUTDOWN_EVENT_TYPE.equals(event.eventTarget.eventType)) {
                final EventContext eventContext = new EventContext(() -> descriptorsCounter.incrementAndGet(),
                        registeredEventHandlers,
                        ref -> currentPageSnapshot.get().refs.get(ref),
                        s -> Optional.empty(),
                        this,
                        out);
                event.eventHandler.accept(eventContext);
            }
        });
    }

    @Override
    public void extractProperty(int descriptorId, String value) {
        log.debug(l -> l.log("extractProperty:" + descriptorId + " value=" + value));
        final var cf = registeredEventHandlers.get(descriptorId);
        if (cf != null) {
            cf.complete(value);
            registeredEventHandlers.remove(descriptorId);
        }
    }

    @Override
    public void evalJsResponse(int descriptorId, String value) {
        log.debug(l -> l.log("evalJsResponse:" + descriptorId + " value=" + value));
        final var cf = registeredEventHandlers.get(descriptorId);
        if (cf != null) {
            cf.complete(value);
            registeredEventHandlers.remove(descriptorId);
        }
    }

    @Override
    public void domEvent(int renderNumber, Path path, String eventType, Function<String, Optional<String>> eventObject) {
        synchronized (this) {
            Path eventElementPath = path;
            while(eventElementPath.level() > 0) {
                final Event event = currentPageSnapshot.get().events.get(new Event.Target(eventType, eventElementPath));
                if (event != null && event.eventTarget.eventType.equals(eventType)) {
                    final EventContext eventContext = new EventContext(() -> descriptorsCounter.incrementAndGet(),
                            registeredEventHandlers,
                            ref -> currentPageSnapshot.get().refs.get(ref),
                            eventObject,
                            this,
                            out);
                    event.eventHandler.accept(eventContext);
                    break;
                } else {
                    final Optional<Path> parentPath = eventElementPath.parent();
                    if (parentPath.isPresent()) {
                        eventElementPath = parentPath.get();
                    } else {
                        // TODO warn
                        break;
                    }
                }
            }
        }
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return scheduledExecutorService.scheduleAtFixedRate(() -> {
            synchronized (this) {
                command.run();
            }
        }, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return scheduledExecutorService.schedule(() -> {
            synchronized (this) {
                command.run();
            }
        }, delay, unit);
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
