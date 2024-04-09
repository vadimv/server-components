package rsp.page;

import rsp.component.Component;
import rsp.dom.*;
import rsp.html.Window;
import rsp.ref.Ref;
import rsp.ref.TimerRef;
import rsp.server.*;
import rsp.server.http.*;
import rsp.util.data.Either;
import rsp.util.json.JsonDataType;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * A server-side session object representing an open browser's page.
 */
public final class LivePageSession implements RemoteIn, Schedule {
    private static final System.Logger logger = System.getLogger(LivePageSession.class.getName());

    public static final String HISTORY_ENTRY_CHANGE_EVENT_NAME = "popstate";

    private final QualifiedSessionId qsid;
    private final PageStateOrigin pageStateOrigin;
    private final Schedules schedules;
    private final Component<?> rootComponent;
    private final RemoteOut remoteOut;

    private final Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers = new HashMap<>();

    private int descriptorsCounter;

    public LivePageSession(final QualifiedSessionId qsid,
                           final PageStateOrigin pageStateOrigin,
                           final Schedules schedules,
                           final Component<?> rootComponent,
                           final RemoteOut remoteOut) {
        this.qsid = Objects.requireNonNull(qsid);
        this.pageStateOrigin = pageStateOrigin;
        this.schedules = Objects.requireNonNull(schedules);
        this.rootComponent = Objects.requireNonNull(rootComponent);
        this.remoteOut = Objects.requireNonNull(remoteOut);
    }

    public synchronized void init() {
        remoteOut.listenEvents(rootComponent.recursiveEvents());
  }

    public void shutdown() {
        logger.log(DEBUG, () -> "Live Page shutdown: " + this);
        synchronized (this) {
            schedules.cancelAll();
        }
    }

    public QualifiedSessionId getId() {
        return qsid;
    }

    @Override
    public synchronized void scheduleAtFixedRate(final Runnable command, final TimerRef key, final long initialDelay, final long period, final TimeUnit unit) {
        logger.log(DEBUG, () -> "Scheduling a periodical task " + key + " with delay: " + initialDelay + ", and period: " + period + " " + unit);
        schedules.scheduleAtFixedRate(() -> {
                                        synchronized (LivePageSession.this) {
                                            command.run();
                                        }
                                    }, key, initialDelay, period, unit);
    }

    @Override
    public synchronized void schedule(final Runnable command, final TimerRef key, final long delay, final TimeUnit unit) {
        logger.log(DEBUG, () -> "Scheduling a delayed task " + key + " with delay: " + delay + " " + unit);
        schedules.schedule(() -> {
                                    synchronized (LivePageSession.this) {
                                        command.run();
                                    }
                                }, key, delay, unit);
    }

    @Override
    public synchronized void cancel(final TimerRef key) {
        logger.log(DEBUG, () -> "Cancelling the task " + key);
        schedules.cancel(key);
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
                }, v -> {
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
            VirtualDomPath eventElementPath = eventPath;
            while (eventElementPath.level() > 0) {
                for (final Event event: rootComponent.recursiveEvents()) {
                    if (event.eventTarget.elementPath.equals(eventElementPath) && event.eventTarget.eventType.equals(eventType)) {
                        event.eventHandler.accept(createEventContext(eventElementPath, eventObject));
                    }
                }
                if (eventElementPath.parent().isPresent()) {
                    eventElementPath = eventElementPath.parent().get();
                } else {
                    break;
                }
            }
        }
    }

    private EventContext createEventContext(final VirtualDomPath eventElementPath,
                                            final JsonDataType.Object eventObject) {
        return new EventContext(eventElementPath,
                                this::evalJs,
                                this::createPropertiesHandle,
                                eventObject,
                                this::dispatchEvent,
                                this,
                                this::setHref);
    }

    private PropertiesHandle createPropertiesHandle(final Ref ref) {
        final VirtualDomPath path = resolveRef(ref);
        if (path == null) {
            throw new IllegalStateException("Ref not found: " + ref);
        }
        return new PropertiesHandle(path, () -> ++descriptorsCounter, registeredEventHandlers, remoteOut);
    }

    private void dispatchEvent(VirtualDomPath eventElementPath, CustomEvent customEvent) {
        handleDomEvent(0, eventElementPath, customEvent.eventName(), customEvent.eventData());
    }

    private VirtualDomPath resolveRef(final Ref ref) {
        return ref instanceof Window.WindowRef ? VirtualDomPath.DOCUMENT : rootComponent.recursiveRefs().get(ref); //TODO check for null
    }

    public CompletableFuture<JsonDataType> evalJs(final String js) {
        logger.log(DEBUG, () -> "Called an JS evaluation: " + js);
        synchronized (this) {
            final int newDescriptor = ++descriptorsCounter;
            final CompletableFuture<JsonDataType> resultHandler = new CompletableFuture<>();
            registeredEventHandlers.put(newDescriptor, resultHandler);
            remoteOut.evalJs(newDescriptor, js);
            return resultHandler;
        }
    }

    private void setHref(final String path) {
        remoteOut.setHref(path);
    }
}