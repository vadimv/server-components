package rsp.page;

import rsp.dom.Event;
import rsp.dom.TreePositionPath;
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
import static rsp.page.PageRendering.DOCUMENT_DOM_PATH;

/**
 * A server-side session object representing an open browser's page.
 */
public final class LivePageSession implements RemoteIn {
    private static final System.Logger logger = System.getLogger(LivePageSession.class.getName());

    private final PageRenderContext pageRenderContext;
    private final RemoteOut remoteOut;
    private final Object sessionLock;

    private final Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers = new HashMap<>();

    private int descriptorsCounter;

    public LivePageSession(final PageRenderContext pageRenderContext,
                           final RemoteOut remoteOut,
                           final Object sessionLock) {
        this.pageRenderContext = Objects.requireNonNull(pageRenderContext);
        this.remoteOut = Objects.requireNonNull(remoteOut);
        this.sessionLock = Objects.requireNonNull(sessionLock);
    }

    public void init() {
        synchronized (sessionLock) {
            remoteOut.listenEvents(pageRenderContext.recursiveEvents());
        }
  }

    public void shutdown() {
        logger.log(DEBUG, () -> "Live Page shutdown: " + this);
        synchronized (sessionLock) {
            pageRenderContext.shutdown();
        }
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
        logger.log(DEBUG, () -> "evalJsResponse: " + descriptorId + " value: " + value.toString());
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
                               final TreePositionPath eventPath,
                               final String eventType,
                               final JsonDataType.Object eventObject) {
        logger.log(DEBUG, () -> "DOM event " + renderNumber + ", componentPath: " + eventPath + ", type: " + eventType + ", event data: " + eventObject);
        synchronized (sessionLock) {
            TreePositionPath eventElementPath = eventPath;
            while (eventElementPath.level() >= 0) {
                for (final Event event: pageRenderContext.recursiveEvents()) {
                    if (event.eventTarget.elementPath().equals(eventElementPath) && event.eventTarget.eventType().equals(eventType)) {
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

    private EventContext createEventContext(final TreePositionPath eventElementPath,
                                            final JsonDataType.Object eventObject) {
        return new EventContext(eventElementPath,
                                this::evalJs,
                                this::createPropertiesHandle,
                                eventObject,
                                this::dispatchEvent,
                                this::setHref);
    }

    private PropertiesHandle createPropertiesHandle(final Ref ref) {
        final TreePositionPath path = resolveRef(ref);
        if (path == null) {
            throw new IllegalStateException("Ref not found: " + ref);
        }
        return new PropertiesHandle(path, () -> ++descriptorsCounter, registeredEventHandlers, remoteOut);
    }

    private void dispatchEvent(TreePositionPath eventElementPath, CustomEvent customEvent) {
        handleDomEvent(0, eventElementPath, customEvent.eventName(), customEvent.eventData());
    }

    private TreePositionPath resolveRef(final Ref ref) {
        return ref instanceof WindowDefinition.WindowRef ? DOCUMENT_DOM_PATH : pageRenderContext.recursiveRefs().get(ref); //TODO check for null
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