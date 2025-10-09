package rsp.page;

import rsp.dom.Event;
import rsp.dom.TreePositionPath;
import rsp.html.WindowDefinition;
import rsp.page.events.*;
import rsp.ref.Ref;
import rsp.server.ExtractPropertyResponse;
import rsp.server.RemoteOut;
import rsp.util.json.JsonDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.DEBUG;
import static rsp.page.PageRendering.DOCUMENT_DOM_PATH;

/**
 * A server-side session object representing an open and connected browser's page.
 */
public final class LivePageSession implements Consumer<SessionEvent> {
    private static final System.Logger logger = System.getLogger(LivePageSession.class.getName());

    private final Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers = new HashMap<>();
    private final Reactor<SessionEvent> reactor;

    private PageRenderContext pageRenderContext;
    private RemoteOut remoteOut;
    private int descriptorsCounter;

    public LivePageSession() {
        this.reactor = new Reactor<>(this);
    }

    public Consumer<SessionEvent> eventsConsumer() {
        return reactor;
    }

    public void start() {
        reactor.start();
    }

    @Override
    public void accept(final SessionEvent event) {
        switch (event) {
            case InitSessionEvent e -> init(e);
            case SessionCustomEvent e -> handleDomEvent(0, e.path(), e.customEvent().eventName(), e.customEvent().eventData());
            case DomEvent e -> handleDomEvent(e.renderNumber(), e.path(), e.eventType(), e.eventObject());
            case EvalJsResponseEvent e -> handleEvalJsResponse(e.descriptorId(), e.value());
            case ExtractPropertyResponseEvent e -> handleExtractPropertyResponse(e.descriptorId(), e.result());
            case RemoteCommand e -> e.accept(remoteOut);
            case GenericTaskEvent e -> e.task().run();
            case ShutdownSessionEvent __ -> shutdown();
        }
    }

    private void init(final InitSessionEvent e) {
        this.pageRenderContext = Objects.requireNonNull(e.pageRenderContext());
        this.remoteOut = Objects.requireNonNull(e.remoteOut());
        this.accept(new RemoteCommand.ListenEvent(pageRenderContext.recursiveEvents()));
    }

    private void shutdown() {
        logger.log(DEBUG, () -> "Live Page shutdown: " + this);
        pageRenderContext.shutdown();
        reactor.stop();
    }

    private void handleExtractPropertyResponse(final int descriptorId, final ExtractPropertyResponse result) {
        if (result instanceof ExtractPropertyResponse.NotFound) {
            logger.log(DEBUG, () -> "extractProperty: " + descriptorId + " failed");
            final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
            if (cf != null) {
                cf.completeExceptionally(new RuntimeException("Extract property: " + descriptorId + " not found"));
                registeredEventHandlers.remove(descriptorId);
            }
        } else if (result instanceof ExtractPropertyResponse.Value v) {
            logger.log(DEBUG, () -> "extractProperty: " + descriptorId + " value: " + v.value());
            final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
            if (cf != null) {
                cf.complete(v.value());
                registeredEventHandlers.remove(descriptorId);
            }
        }
    }

    private void handleEvalJsResponse(final int descriptorId, final JsonDataType value) {
        logger.log(DEBUG, () -> "evalJsResponse: " + descriptorId + " value: " + value.toString());
        final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
        if (cf != null) {
            cf.complete(value);
            registeredEventHandlers.remove(descriptorId);
        }
    }

    private void handleDomEvent(final int renderNumber,
                                final TreePositionPath eventPath,
                                final String eventType,
                                final JsonDataType.Object eventObject) {
        logger.log(DEBUG, () -> "DOM event " + renderNumber + ", componentPath: " + eventPath + ", type: " + eventType + ", event data: " + eventObject);
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

    private EventContext createEventContext(final TreePositionPath eventElementPath,
                                            final JsonDataType.Object eventObject) {
        return new EventContext(eventElementPath,
                                this::evalJs,
                                this::createPropertiesHandle,
                                eventObject,
                                (path, customEvent) -> reactor.accept(new SessionCustomEvent(path, customEvent)),
                                this::setHref);
    }

    private PropertiesHandle createPropertiesHandle(final Ref ref) {
        final TreePositionPath path = resolveRef(ref);
        if (path == null) {
            throw new IllegalStateException("Ref not found: " + ref);
        }
        return new PropertiesHandle(path, () -> ++descriptorsCounter, registeredEventHandlers, this);
    }

    private TreePositionPath resolveRef(final Ref ref) {
        return ref instanceof WindowDefinition.WindowRef ? DOCUMENT_DOM_PATH : pageRenderContext.recursiveRefs().get(ref); //TODO check for null
    }

    private CompletableFuture<JsonDataType> evalJs(final String js) {
        logger.log(DEBUG, () -> "Called an JS evaluation: " + js);
        final int newDescriptor = ++descriptorsCounter;
        final CompletableFuture<JsonDataType> resultHandler = new CompletableFuture<>();
        registeredEventHandlers.put(newDescriptor, resultHandler);
        this.accept(new RemoteCommand.EvalJs(newDescriptor, js));
        return resultHandler;
    }

    private void setHref(final String path) {
        this.accept(new RemoteCommand.SetHref(path));
    }
}