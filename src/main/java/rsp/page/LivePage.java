package rsp.page;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.html.WindowRef;
import rsp.ref.Ref;
import rsp.server.InMessages;
import rsp.server.OutMessages;
import rsp.util.data.Either;
import rsp.util.json.JsonDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * A server-side session object of an open browser's page.
 * @param <S> the application's state's type
 */
public final class LivePage<S> implements InMessages, Schedule {
    private static final System.Logger logger = System.getLogger(LivePage.class.getName());

    public final QualifiedSessionId qsid;
    private final LivePageState<S> pageState;
    private final ScheduledExecutorService scheduledExecutorService;
    private final OutMessages out;

    private int descriptorsCounter;
    private final Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers = new HashMap<>();
    private final Map<Object, ScheduledFuture<?>> schedules = new HashMap<>();

    public LivePage(final QualifiedSessionId qsid,
                    final LivePageState<S> pageState,
                    final ScheduledExecutorService scheduledExecutorService,
                    final OutMessages out) {
        this.qsid = qsid;
        this.pageState = pageState;
        this.scheduledExecutorService = scheduledExecutorService;
        this.out = out;
    }


    public void shutdown() {
        logger.log(DEBUG, () -> "Live Page shutdown: " + this);
        synchronized (pageState) {
            for (final var timer : schedules.entrySet()) {
                timer.getValue().cancel(true);
            }
        }
    }

    @Override
    public void handleExtractPropertyResponse(final int descriptorId, final Either<Throwable, JsonDataType> result) {
        result.on(ex -> {
                    logger.log(DEBUG, () -> "extractProperty: " + descriptorId + " exception: " + ex.getMessage());
                    synchronized (pageState) {
                        final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
                        if (cf != null) {
                            cf.completeExceptionally(ex);
                            registeredEventHandlers.remove(descriptorId);
                        }
                    }
                },
                 v -> {
                     logger.log(DEBUG, () -> "extractProperty: " + descriptorId + " value: " + v.toStringValue());
                     synchronized (pageState) {
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
        synchronized (pageState) {
            final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
            if (cf != null) {
                cf.complete(value);
                registeredEventHandlers.remove(descriptorId);
            }
        }
    }

    @Override
    public void handleDomEvent(final int renderNumber, final VirtualDomPath path, final String eventType, final JsonDataType.Object eventObject) {
        synchronized (pageState) {
            VirtualDomPath eventElementPath = path;
            while(eventElementPath.level() > 0) {
                final Event event = pageState.snapshot().events.get(new Event.Target(eventType, eventElementPath));
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
    public synchronized Timer scheduleAtFixedRate(final Runnable command,
                                                  final Object key,
                                                  final long initialDelay, final long period, final TimeUnit unit) {
        final ScheduledFuture<?> timer = scheduledExecutorService.scheduleAtFixedRate(() -> {
            synchronized (pageState) {
                command.run();
            }
        }, initialDelay, period, unit);
        schedules.put(key, timer);
        return new Timer(key, () -> cancel(key));
    }

    @Override
    public synchronized Timer schedule(final Runnable command, final Object key, final long delay, final TimeUnit unit) {
        final ScheduledFuture<?> timer =  scheduledExecutorService.schedule(() -> {
            synchronized (pageState) {
                command.run();
            }
        }, delay, unit);
        schedules.put(key, timer);
        return new Timer(key, () -> cancel(key));
    }

    @Override
    public synchronized void cancel(final Object key) {
        final ScheduledFuture<?> schedule = schedules.get(key);
        if (schedule != null) {
            schedule.cancel(true);
            schedules.remove(key);
        }
    }

    private EventContext createEventContext(final JsonDataType.Object eventObject) {
        return new EventContext(qsid,
                                js -> evalJs(js),
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
        return ref instanceof WindowRef ? VirtualDomPath.DOCUMENT : pageState.snapshot().refs.get(ref);
    }

    public CompletableFuture<JsonDataType> evalJs(final String js) {
        synchronized (pageState) {
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

    private void pushHistory(final String path) {
        out.pushHistory(path);
    }
}