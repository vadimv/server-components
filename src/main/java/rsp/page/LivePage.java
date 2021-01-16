package rsp.page;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.dsl.Ref;
import rsp.dsl.WindowRef;
import rsp.server.InMessages;
import rsp.server.OutMessages;
import rsp.util.Log;
import rsp.util.json.JsonDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A server-side mirror object of an open browser's page.
 * @param <S> the application's state's type
 */
public final class LivePage<S> implements InMessages, Schedule {
    public static final String POST_START_EVENT_TYPE = "page-start";
    public static final String POST_SHUTDOWN_EVENT_TYPE = "page-shutdown";

    private final QualifiedSessionId qsid;
    private final LivePageState<S> pageState;
    private final ScheduledExecutorService scheduledExecutorService;
    private final OutMessages out;
    private final Log.Reporting log;

    private int descriptorsCounter;
    private final Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers = new HashMap<>();

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
        synchronized (pageState) {
            // Invoke this page's shutdown events
            pageState.snapshot().events.values().forEach(event -> { // TODO should these events to be ordered by its elements paths?
                if (POST_SHUTDOWN_EVENT_TYPE.equals(event.eventTarget.eventType)) {
                    final EventContext eventContext = createEventContext(JsonDataType.Object.EMPTY);
                    event.eventHandler.accept(eventContext);
                }
            });
        }
    }

    @Override
    public void handleExtractPropertyResponse(int descriptorId, JsonDataType value) {
        log.debug(l -> l.log("extractProperty: " + descriptorId + " value: " + value.toStringValue()));
        synchronized (pageState) {
            final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
            if (cf != null) {
                cf.complete(value);
                registeredEventHandlers.remove(descriptorId);
            }
        }
    }

    @Override
    public void handleEvalJsResponse(int descriptorId, JsonDataType value) {
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
    public void handleDomEvent(int renderNumber, VirtualDomPath path, String eventType, JsonDataType.Object eventObject) {
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
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return scheduledExecutorService.scheduleAtFixedRate(() -> {
            synchronized (pageState) {
                command.run();
            }
        }, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return scheduledExecutorService.schedule(() -> {
            synchronized (pageState) {
                command.run();
            }
        }, delay, unit);
    }

    private EventContext createEventContext(JsonDataType.Object eventObject) {
        return new EventContext(qsid,
                                js -> evalJs(js),
                                ref -> createPropertiesHandle(ref),
                                eventObject,
                                this,
                                href -> setHref(href));
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