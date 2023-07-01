package rsp.page;

import rsp.component.Component;
import rsp.dom.*;
import rsp.html.WindowRef;
import rsp.ref.Ref;
import rsp.server.HttpRequest;
import rsp.server.In;
import rsp.server.Out;
import rsp.server.Path;
import rsp.util.Lookup;
import rsp.util.data.Either;
import rsp.util.json.JsonDataType;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * A server-side object representing an open browser's page.
 */
public final class LivePage implements In, Schedule, Lookup {
    private static final System.Logger logger = System.getLogger(LivePage.class.getName());

    public static String HISTORY_ENTRY_CHANGE_EVENT_NAME = "popstate";

    public final QualifiedSessionId qsid;

    private final ScheduledExecutorService scheduledExecutorService;
    private final Component<?, ?> rootComponent;
    private final Out out;
    private final Path basePath;
    private final HttpRequest httpRequest;

    private final Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers = new HashMap<>();
    private final Map<Object, ScheduledFuture<?>> schedules = new HashMap<>();

    private int descriptorsCounter;
    private Path path;

    public LivePage(final QualifiedSessionId qsid,
                    final Path basePath,
                    final HttpRequest httpRequest,
                    final ScheduledExecutorService scheduledExecutorService,
                    final Component<?, ?> rootComponent,
                    final Out out) {
        this.qsid = Objects.requireNonNull(qsid);
        this.basePath = Objects.requireNonNull(basePath);
        this.httpRequest = Objects.requireNonNull(httpRequest);
        this.path = Objects.requireNonNull(httpRequest.path);
        this.scheduledExecutorService = Objects.requireNonNull(scheduledExecutorService);
        this.rootComponent = Objects.requireNonNull(rootComponent);
        this.out = Objects.requireNonNull(out);
    }


    public void shutdown() {
        logger.log(DEBUG, () -> "Live Page shutdown: " + this);
        synchronized (this) {
            for (final var timer : schedules.entrySet()) {
                timer.getValue().cancel(true);
            }
        }
    }

    @Override
    public synchronized Timer scheduleAtFixedRate(final Runnable command, final Object key, final long initialDelay, final long period, final TimeUnit unit) {
        logger.log(DEBUG, () -> "Scheduling a periodical task " + key + " with delay: " + initialDelay + ", and period: " + period + " " + unit);
        final ScheduledFuture<?> timer = scheduledExecutorService.scheduleAtFixedRate(() -> {
            synchronized (this) {
                command.run();
            }
        }, initialDelay, period, unit);
        schedules.put(key, timer);
        return new Timer(key, () -> cancel(key));
    }

    @Override
    public synchronized Timer schedule(final Runnable command, final Object key, final long delay, final TimeUnit unit) {
        logger.log(DEBUG, () -> "Scheduling a delayed task " + key + " with delay: " + delay + " " + unit);
        final ScheduledFuture<?> timer =  scheduledExecutorService.schedule(() -> {
            synchronized (this) {
                command.run();
            }
        }, delay, unit);
        schedules.put(key, timer);
        return new Timer(key, () -> cancel(key));
    }

    @Override
    public synchronized void cancel(final Object key) {
        logger.log(DEBUG, () -> "Cancelling the task " + key);
        final ScheduledFuture<?> schedule = schedules.get(key);
        if (schedule != null) {
            schedule.cancel(true);
            schedules.remove(key);
        }
    }

    @Override
    public void handleExtractPropertyResponse(final int descriptorId, final Either<Throwable, JsonDataType> result) {
        result.on(ex -> {
                    logger.log(DEBUG, () -> "extractProperty: " + descriptorId + " exception: " + ex.getMessage());
                    synchronized (this) {
                        final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
                        if (cf != null) {
                            cf.completeExceptionally(ex);
                            registeredEventHandlers.remove(descriptorId);
                        }
                    }
                },
                v -> {
                    logger.log(DEBUG, () -> "extractProperty: " + descriptorId + " value: " + v.toStringValue());
                    synchronized (this) {
                        final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
                        if (cf != null) {
                            cf.complete(v);
                            registeredEventHandlers.remove(descriptorId);
                        }
                    }
                });
    }

    @Override
    public void handleEvalJsResponse(final int descriptorId, final JsonDataType value) {
        logger.log(DEBUG, () -> "evalJsResponse: " + descriptorId + " value: " + value.toStringValue());
        synchronized (this) {
            final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
            if (cf != null) {
                cf.complete(value);
                registeredEventHandlers.remove(descriptorId);
            }
        }
    }

    @Override
    public void handleDomEvent(final int renderNumber, final VirtualDomPath eventPath, final String eventType, final JsonDataType.Object eventObject) {
        logger.log(DEBUG, () -> "DOM event " + renderNumber + ", path: " + eventPath + ", type: " + eventType + ", event data: " + eventObject);
        synchronized (this) {
            if (HISTORY_ENTRY_CHANGE_EVENT_NAME.equals(eventType) && VirtualDomPath.WINDOW.equals(eventPath)) {
                this.path = Path.of("/0/0"); // TODO
                rootComponent.resolveState(false);
            } else {
                final Map<Event.Target, Event> events = rootComponent.recursiveEvents();
                final EventContext eventContext = createEventContext(eventObject);
                VirtualDomPath eventElementPath = eventPath;
                while(eventElementPath.level() > 0) {
                    final Event event = events.get(new Event.Target(eventType, eventElementPath));
                    if (event != null && event.eventTarget.eventType.equals(eventType)) {
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
    }

    private EventContext createEventContext(final JsonDataType.Object eventObject) {
        return new EventContext(js -> evalJs(js),
                                ref -> createPropertiesHandle(ref),
                                eventObject,
                                this,
                                href -> setHref(href));
    }

    private PropertiesHandle createPropertiesHandle(final Ref ref) {
        final VirtualDomPath path = resolveRef(ref);
        if (path == null) {
            throw new IllegalStateException("Ref not found: " + ref);
        }
        return new PropertiesHandle(path, () -> ++descriptorsCounter, registeredEventHandlers, out);
    }

    private VirtualDomPath resolveRef(final Ref ref) {
        return ref instanceof WindowRef ? VirtualDomPath.DOCUMENT : rootComponent.recursiveRefs().get(ref); //TODO check for null
    }

    public CompletableFuture<JsonDataType> evalJs(final String js) {
        logger.log(DEBUG, () -> "Called an JS evaluation: " + js);
        synchronized (this) {
            final int newDescriptor = ++descriptorsCounter;
            final CompletableFuture<JsonDataType> resultHandler = new CompletableFuture<>();
            registeredEventHandlers.put(newDescriptor, resultHandler);
            out.evalJs(newDescriptor, js);
            return resultHandler;
        }
    }

    private void setHref(final String path) {
        out.setHref(path);
    }

    public Set<VirtualDomPath> updateElements(final Tag oldTag,
                                              final Tag newTag) {
        // Calculate diff between currentContext and newContext
        final DefaultDomChangesContext domChangePerformer = new DefaultDomChangesContext();
        new Diff(Optional.of(oldTag), newTag, domChangePerformer).run();
        if ( domChangePerformer.commands.size() > 0) {
            out.modifyDom(domChangePerformer.commands);
        }
        return domChangePerformer.elementsToRemove;
    }

    public void updateEvents(final Set<Event> oldEvents,
                             final Set<Event> newEvents,
                             final Set<VirtualDomPath> elementsToRemove) {
        // Unregister events
        final List<Event> eventsToRemove = new ArrayList<>();
        for(Event event : oldEvents) {
            if(!newEvents.contains(event) && !elementsToRemove.contains(event.eventTarget.elementPath)) {
                eventsToRemove.add(event);
            }
        }
        eventsToRemove.forEach(event -> {
            final Event.Target eventTarget = event.eventTarget;
            out.forgetEvent(eventTarget.eventType,
                            eventTarget.elementPath);
        });

        // Register new event types on client
        final List<Event> eventsToAdd = new ArrayList<>();
        for(Event event : newEvents) {
            if(!oldEvents.contains(event)) {
                eventsToAdd.add(event);
            }
        }
        out.listenEvents(eventsToAdd);
    }

    /**
     * Updates browser's navigation.
     * @param pathOperator
     */
    public void applyToPath(UnaryOperator<Path> pathOperator) {
        final Path newPath = pathOperator.apply(path);
        logger.log(DEBUG, () -> "New path after a components path's function application: " + newPath);
        if (!newPath.equals(path)) {
            path = newPath;
            out.pushHistory(basePath.resolve(newPath).toString());
            logger.log(DEBUG, () -> "Path update: " + newPath);
        }
    }

    @Override
    public <T> T lookup(final Class<T> clazz) {
        if (HttpRequest.class.equals(clazz)) {
            return (T) httpRequest;
        } else if (Path.class.equals(clazz)) {
            return (T) path;
        } else {
            throw new IllegalStateException("Lookup for an unsupported state reference type: " + clazz);
        }
    }
}