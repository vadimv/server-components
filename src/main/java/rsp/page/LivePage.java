package rsp.page;

import rsp.Component;
import rsp.dom.*;
import rsp.dsl.Ref;
import rsp.dsl.WindowRef;
import rsp.server.InMessages;
import rsp.server.OutMessages;
import rsp.server.Path;
import rsp.state.UseState;
import rsp.util.Log;
import rsp.util.json.JsonDataType;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class LivePage<S> implements InMessages, Schedule {
    public static final String POST_START_EVENT_TYPE = "page-start";
    public static final String POST_SHUTDOWN_EVENT_TYPE = "page-shutdown";



    private final AtomicInteger descriptorsCounter = new AtomicInteger();
    private final Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers = new ConcurrentHashMap<>();

    private final QualifiedSessionId qsid;
    private final LivePageState<S> pageState;
    private final ScheduledExecutorService scheduledExecutorService;
    private final OutMessages out;
    private final Log.Reporting log;

    public LivePage(QualifiedSessionId qsid,
                    LivePageState<S> pageState,
                    ScheduledExecutorService scheduledExecutorService,
                    OutMessages out,
                    Log.Reporting log) {
        this.qsid = qsid;
        this.pageState = pageState;
        this.scheduledExecutorService = scheduledExecutorService;
        this.out = out;
        this.log = log;
    }

    public void shutdown() {
        log.debug(l -> l.log("Live Page shutdown: " + this));
        // Invoke this page's shutdown events
        pageState.snapshot.events.values().forEach(event -> { // TODO should these events to be ordered by its elements paths?
            if (POST_SHUTDOWN_EVENT_TYPE.equals(event.eventTarget.eventType)) {
                final EventContext eventContext = createEventContext(JsonDataType.Object.EMPTY);
                event.eventHandler.accept(eventContext);
            }
        });
    }

    @Override
    public void extractPropertyResponse(int descriptorId, JsonDataType value) {
        log.debug(l -> l.log("extractProperty: " + descriptorId + " value: " + value.toStringValue()));
        final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
        if (cf != null) {
            cf.complete(value);
            registeredEventHandlers.remove(descriptorId);
        }
    }

    @Override
    public void evalJsResponse(int descriptorId, JsonDataType value) {
        log.debug(l -> l.log("evalJsResponse: " + descriptorId + " value: " + value.toStringValue()));
        final var cf = registeredEventHandlers.get(descriptorId);
        if (cf != null) {
            cf.complete(value);
            registeredEventHandlers.remove(descriptorId);
        }
    }

    @Override
    public void domEvent(int renderNumber, VirtualDomPath path, String eventType, JsonDataType.Object eventObject) {
        synchronized (this) {
            VirtualDomPath eventElementPath = path;
            while(eventElementPath.level() > 0) {
                final Event event = pageState.snapshot.events.get(new Event.Target(eventType, eventElementPath));
                if (event != null && event.eventTarget.eventType.equals(eventType)) {
                    final EventContext eventContext = createEventContext(eventObject);
                    event.eventHandler.accept(eventContext);
                    break;
                } else {
                    final Optional<VirtualDomPath> parentPath = eventElementPath.parent();
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

    private EventContext createEventContext(JsonDataType.Object eventObject) {
        return new EventContext(qsid,
                                js -> evalJs(js),
                                ref -> createPropertiesHandle(ref),
                                (JsonDataType.Object) eventObject,
                                this,
                                href -> setHref(href));
    }

    private PropertiesHandle createPropertiesHandle(Ref ref) {
        final VirtualDomPath path = resolveRef(ref);
        if (path == null) {
            throw new IllegalStateException("Ref not found: " + ref);
        }
        return new PropertiesHandle(path, () -> descriptorsCounter.incrementAndGet(), registeredEventHandlers, out);
    }

    private VirtualDomPath resolveRef(Ref ref) {
        return ref instanceof WindowRef ? VirtualDomPath.DOCUMENT : pageState.snapshot.refs.get(ref);
    }

    public CompletableFuture<JsonDataType> evalJs(String js) {
        final Integer newDescriptor = descriptorsCounter.incrementAndGet();
        final CompletableFuture<JsonDataType> resultHandler = new CompletableFuture<>();
        registeredEventHandlers.put(newDescriptor, resultHandler);
        out.evalJs(newDescriptor, js);
        return resultHandler;
    }

    private void setHref(String path) {
        out.setHref(path);
    }

    private void pushHistory(String path) {
        out.pushHistory(path);
    }



    public static class LivePageState<S> implements UseState<S> {
        private final QualifiedSessionId qsid;
        private final StateToRouteDispatch<S> state2route;
        private final Component<S> rootComponent;
        private final BiFunction<String, RenderContext, RenderContext> enrich;
        private final OutMessages out;

        private S state;
        private Snapshot snapshot;

        public LivePageState(Snapshot snapshot,
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
            final var currentRoot = snapshot.domRoot;
            final var domChangePerformer = new DefaultDomChangesPerformer();
            new Diff(currentRoot, newContext.root, domChangePerformer).run();
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

            snapshot = new Snapshot(newPath, Optional.of(newContext.root), newContext.events, newContext.refs);
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
    }

    public static class Snapshot {
        public final Path path;
        public final Optional<Tag> domRoot;
        public final Map<Event.Target, Event> events;
        public final Map<Ref, VirtualDomPath> refs;

        public Snapshot(Path path,
                        Optional<Tag> domRoot,
                        Map<Event.Target, Event> events,
                        Map<Ref, VirtualDomPath> refs) {
            this.path = path;
            this.domRoot = domRoot;
            this.events = events;
            this.refs = refs;
        }
    }
}
