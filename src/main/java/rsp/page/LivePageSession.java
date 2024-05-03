package rsp.page;

import rsp.component.Component;
import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.html.WindowDefinition;
import rsp.ref.Ref;
import rsp.server.ExtractPropertyResponse;
import rsp.server.RemoteIn;
import rsp.server.RemoteOut;
import rsp.util.json.JsonDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * A server-side session object representing an open browser's page.
 */
public final class LivePageSession implements RemoteIn {
    private static final System.Logger logger = System.getLogger(LivePageSession.class.getName());

    public static final String HISTORY_ENTRY_CHANGE_EVENT_NAME = "popstate";

    private final QualifiedSessionId qsid;
    private final Component<?> rootComponent;
    private final RemoteOut remoteOut;
    private final Object sessionLock;

    private final Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers = new HashMap<>();

    private int descriptorsCounter;

    public LivePageSession(final QualifiedSessionId qsid,
                           final Component<?> rootComponent,
                           final RemoteOut remoteOut,
                           final Object sessionLock) {
        this.qsid = Objects.requireNonNull(qsid);
        this.rootComponent = Objects.requireNonNull(rootComponent);
        this.remoteOut = Objects.requireNonNull(remoteOut);
        this.sessionLock = Objects.requireNonNull(sessionLock);
    }

    public synchronized void init() {
        remoteOut.listenEvents(rootComponent.recursiveEvents());
  }

    public void shutdown() {
        logger.log(DEBUG, () -> "Live Page shutdown: " + this);
        synchronized (sessionLock) {
            rootComponent.unmount();
        }
    }

    public QualifiedSessionId getId() {
        return qsid;
    }


    @Override
    public void handleExtractPropertyResponse(final int descriptorId, final ExtractPropertyResponse result) {
        if (result instanceof ExtractPropertyResponse.NotFound) {
            logger.log(DEBUG, () -> "extractProperty: " + descriptorId + " failed");
            synchronized (sessionLock) {
                final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
                if (cf != null) {
                    cf.completeExceptionally(new RuntimeException("Extract property: " + descriptorId + " not found"));
                    registeredEventHandlers.remove(descriptorId);
                }
            }
        } else if (result instanceof ExtractPropertyResponse.Value v) {
            logger.log(DEBUG, () -> "extractProperty: " + descriptorId + " value: " + v.value());
            synchronized (sessionLock) {
                final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
                if (cf != null) {
                    cf.complete(v.value());
                    registeredEventHandlers.remove(descriptorId);
                    }
                }
            }
    }

    @Override
    public void handleEvalJsResponse(final int descriptorId, final JsonDataType value) {
        logger.log(DEBUG, () -> "evalJsResponse: " + descriptorId + " value: " + value.toStringValue());
        synchronized (sessionLock) {
            final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
            if (cf != null) {
                cf.complete(value);
                registeredEventHandlers.remove(descriptorId);
            }
        }
    }

    @Override
    public void handleDomEvent(final int renderNumber,
                               final VirtualDomPath eventPath,
                               final String eventType,
                               final JsonDataType.Object eventObject) {
        logger.log(DEBUG, () -> "DOM event " + renderNumber + ", path: " + eventPath + ", type: " + eventType + ", event data: " + eventObject);
        synchronized (sessionLock) {
            VirtualDomPath eventElementPath = eventPath;
            while (eventElementPath.level() >= 0) {
                for (final Event event: rootComponent.recursiveEvents()) {
                    if (event.eventTarget.elementPath.equals(eventElementPath) && event.eventTarget.eventType.equals(eventType)) {
                        event.eventHandler.accept(createEventContext(eventElementPath, eventObject));
                    }
                }
                if (eventElementPath.level() > 0) {
                    eventElementPath = eventElementPath.parent();
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
        return ref instanceof WindowDefinition.WindowRef ? VirtualDomPath.DOCUMENT : rootComponent.recursiveRefs().get(ref); //TODO check for null
    }

    public CompletableFuture<JsonDataType> evalJs(final String js) {
        logger.log(DEBUG, () -> "Called an JS evaluation: " + js);
        synchronized (sessionLock) {
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