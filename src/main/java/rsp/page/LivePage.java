package rsp.page;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.dsl.WindowRef;
import rsp.ref.Ref;
import rsp.server.InMessages;
import rsp.server.OutMessages;
import rsp.util.data.Either;
import rsp.util.logging.Log;
import rsp.util.json.JsonDataType;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * A server-side mirror object of an open browser's page.
 * @param <S> the application's state's type
 */
public final class LivePage<S> implements InMessages, Schedule<S> {

    private final QualifiedSessionId qsid;
    private final LivePageState<S> pageState;
    private final ScheduledExecutorService scheduledExecutorService;
    private final OutMessages out;
    private final Log.Reporting log;

    private int descriptorsCounter;
    private final Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers = new HashMap<>();
    private final Map<Object, ScheduledFuture<?>> schedules = new HashMap<>();

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

    public S getPageState() {
        return pageState.get();
    }

    public void shutdown() {
        log.debug(l -> l.log("Live Page shutdown: " + this));
        synchronized (pageState) {
            for (var timer : schedules.entrySet()) {
                timer.getValue().cancel(true);
            }
        }
    }

    @Override
    public void handleExtractPropertyResponse(int descriptorId,
                                              Either<Throwable,
                                              JsonDataType> result) {
        result.on(ex -> {
                    log.debug(l -> l.log("extractProperty: " + descriptorId + " exception: " + ex.getMessage()));
                    synchronized (pageState) {
                        final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
                        if (cf != null) {
                            cf.completeExceptionally(ex);
                            registeredEventHandlers.remove(descriptorId);
                        }
                    }
                },
                 v -> {
                     log.debug(l -> l.log("extractProperty: " + descriptorId + " value: " + v.toStringValue()));
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
    public void handleEvalJsResponse(int descriptorId,
                                     JsonDataType value) {
        log.debug(l -> l.log("evalJsResponse: " + descriptorId + " value: " + value.toStringValue()));
        synchronized (pageState) {
            final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
            if (cf != null) {
                cf.complete(value);
                registeredEventHandlers.remove(descriptorId);
            }
        }
    }

    @Override
    public void handleDomEvent(int renderNumber,
                               VirtualDomPath path,
                               String eventType,
                               JsonDataType.Object eventObject) {
        synchronized (pageState) {
            VirtualDomPath eventElementPath = path;
            while(eventElementPath.level() > 0) {
                final Event event = pageState.snapshot().events.get(new Event.Target(eventType, eventElementPath));
                if (event != null && event.eventTarget.eventType.equals(eventType)) {
                    final EventContext eventContext = createEventContext(eventObject, event.componentSetState);
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
    public synchronized Timer scheduleAtFixedRate(Consumer<S> command,
                                                  Object key,
                                                  long initialDelay,
                                                  long period,
                                                  TimeUnit unit) {
        final ScheduledFuture<?> timer = scheduledExecutorService.scheduleAtFixedRate(() -> {
            synchronized (pageState) {
                command.accept(pageState.get());
            }
        }, initialDelay, period, unit);
        schedules.put(key, timer);
        return new Timer(key, () -> cancel(key));
    }

    @Override
    public synchronized Timer schedule(Consumer<S> command,
                                       Object key,
                                       long delay,
                                       TimeUnit unit) {
        final ScheduledFuture<?> timer =  scheduledExecutorService.schedule(() -> {
            synchronized (pageState) {
                command.accept(pageState.get());
            }
        }, delay, unit);
        schedules.put(key, timer);
        return new Timer(key, () -> cancel(key));
    }

    @Override
    public synchronized void cancel(Object key) {
        final ScheduledFuture<?> schedule = schedules.get(key);
        if (schedule != null) {
            schedule.cancel(true);
            schedules.remove(key);
        }
    }

    private EventContext<S> createEventContext(JsonDataType.Object eventObject,
                                               Consumer<S> setState) {
        return new EventContext<>(qsid,
                                  js -> evalJs(js),
                                  ref -> createPropertiesHandle(ref),
                                  eventObject,
                                 this,
                                  href -> setHref(href),
                                  setState);
    }

    private PropertiesHandle createPropertiesHandle(Ref ref) {
        final VirtualDomPath path = resolveRef(ref);
        if (path == null) {
            throw new IllegalStateException("Ref not found: " + ref);
        }
        return new PropertiesHandle(path, () -> ++descriptorsCounter, registeredEventHandlers, out);
    }

    private VirtualDomPath resolveRef(Ref ref) {
        return ref instanceof WindowRef ? VirtualDomPath.DOCUMENT : pageState.snapshot().refs.get(ref);
    }

    public CompletableFuture<JsonDataType> evalJs(String js) {
        synchronized (pageState) {
            final int newDescriptor = ++descriptorsCounter;
            final CompletableFuture<JsonDataType> resultHandler = new CompletableFuture<>();
            registeredEventHandlers.put(newDescriptor, resultHandler);
            out.evalJs(newDescriptor, js);
            return resultHandler;
        }
    }

    private void setHref(String path) {
        out.setHref(path);
    }

    private void pushHistory(String path) {
        out.pushHistory(path);
    }
}