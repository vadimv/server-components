package rsp.page;

import rsp.component.ComponentEventEntry;
import rsp.dom.DomEventEntry;
import rsp.dom.TreePositionPath;
import rsp.dsl.Window;
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
import static rsp.page.PageBuilder.DOCUMENT_DOM_PATH;

/**
 * A server-side session object representing an open and connected browser's page / tab.
 * Every live page session has its events loop thread.
 */
public final class LivePageSession implements Consumer<Command> {
    private static final System.Logger logger = System.getLogger(LivePageSession.class.getName());

    private final Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers = new HashMap<>();
    private final Reactor<Command> reactor;

    private PageBuilder pageRenderContext;
    private RemoteOut remoteOut;
    private int descriptorsCounter;

    public LivePageSession(final EventLoop eventLoop) {
        this.reactor = new Reactor<>(this, Objects.requireNonNull(eventLoop));
    }

    public Consumer<Command> eventsConsumer() {
        return reactor;
    }

    public void start() {
        reactor.start();
    }

    @Override
    public void accept(final Command event) {
        Objects.requireNonNull(event);
        switch (event) {
            case InitSessionCommand e -> init(e);
            case SessionCustomEvent e -> handleDomEvent(0, e.path(), e.customEvent().eventName(), e.customEvent().eventData());
            case ComponentEventNotification e -> handleComponentEvent(e.eventType(), e.eventObject());
            case DomEventNotification e -> handleDomEvent(e.renderNumber(), e.path(), e.eventType(), e.eventObject());
            case EvalJsResponseEvent e -> handleEvalJsResponse(e.descriptorId(), e.value());
            case ExtractPropertyResponseEvent e -> handleExtractPropertyResponse(e.descriptorId(), e.result());
            case RemoteCommand e -> e.accept(remoteOut);
            case GenericTaskEvent e -> e.task().run();
            case ShutdownSessionCommand _ -> shutdown();
        }
    }

    private void init(final InitSessionCommand initSessionEvent) {
        this.pageRenderContext = Objects.requireNonNull(initSessionEvent.pageRenderContext());
        this.remoteOut = Objects.requireNonNull(initSessionEvent.remoteOut());
        initSessionEvent.commandsEnqueue().redirect(reactor);
        this.accept(new RemoteCommand.ListenEvent(pageRenderContext.recursiveEvents()
                                                                    .stream()
                                                                    .toList()));
    }

    private void shutdown() {
        logger.log(DEBUG, () -> "Live Page shutdown: " + this);
        pageRenderContext.shutdown();
        reactor.stop();
    }

    private void handleExtractPropertyResponse(final int descriptorId, final ExtractPropertyResponse result) {
        Objects.requireNonNull(result);
        if (result instanceof ExtractPropertyResponse.NotFound) {
            logger.log(DEBUG, () -> "extractProperty: " + descriptorId + " failed");
            final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
            if (cf != null) {
                cf.completeExceptionally(new RuntimeException("Extract property: " + descriptorId + " not found"));
                registeredEventHandlers.remove(descriptorId);
            }
        } else if (result instanceof ExtractPropertyResponse.Value(JsonDataType value)) {
            logger.log(DEBUG, () -> "extractProperty: " + descriptorId + " value: " + value);
            final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
            if (cf != null) {
                cf.complete(value);
                registeredEventHandlers.remove(descriptorId);
            }
        }
    }

    private void handleEvalJsResponse(final int descriptorId, final JsonDataType value) {
        Objects.requireNonNull(value);
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
        Objects.requireNonNull(eventPath);
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(eventObject);
        logger.log(DEBUG, () -> "DOM event " + renderNumber + ", componentPath: " + eventPath + ", type: " + eventType + ", event data: " + eventObject);
        TreePositionPath eventElementPath = eventPath;
        while (eventElementPath.elementsCount() >= 0) {
            for (final DomEventEntry event: pageRenderContext.recursiveEvents()) {
                if (event instanceof DomEventEntry domEventEntry
                    && domEventEntry.eventTarget.elementPath().equals(eventElementPath)
                    && event.eventName.equals(eventType)) {
                    event.eventHandler.accept(createEventContext(eventElementPath, eventObject));
                }
            }
            if (eventElementPath.elementsCount() > 0) {
                eventElementPath = eventElementPath.parent();
            } else {
                break;
            }
        }
    }

    private void handleComponentEvent(final String eventType,
                                      final Object eventObject) {
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(eventObject);
        logger.log(DEBUG, () -> "Component event, type: " + eventType + ", event data: " + eventObject);
        for (final ComponentEventEntry event: pageRenderContext.recursiveComponentEvents()) {
            if (event.eventName().equals(eventType)) {
                event.eventHandler().accept(new ComponentEventEntry.EventContext(eventObject));
            }
        }
    }

    private EventContext createEventContext(final TreePositionPath eventElementPath,
                                            final JsonDataType.Object eventObject) {
        Objects.requireNonNull(eventElementPath);
        Objects.requireNonNull(eventObject);
        return new EventContext(eventElementPath,
                                this::evalJs,
                                this::createPropertiesHandle,
                                eventObject,
                                (path, customEvent) -> reactor.accept(new SessionCustomEvent(path, customEvent)),
                                this::setHref);
    }

    private PropertiesHandle createPropertiesHandle(final Ref ref) {
        Objects.requireNonNull(ref);
        final TreePositionPath path = resolveRef(ref);
        if (path == null) {
            throw new IllegalStateException("Ref not found: " + ref);
        }
        return new PropertiesHandle(path, () -> ++descriptorsCounter, registeredEventHandlers, this);
    }

    private TreePositionPath resolveRef(final Ref ref) {
        Objects.requireNonNull(ref);
        // TODO verify if below condition is correct
        return ref instanceof Window.WindowRef ? DOCUMENT_DOM_PATH : pageRenderContext.recursiveRefs().get(ref); //TODO check for null
    }

    private CompletableFuture<JsonDataType> evalJs(final String js) {
        Objects.requireNonNull(js);
        logger.log(DEBUG, () -> "Called an JS evaluation: " + js);
        final int newDescriptor = ++descriptorsCounter;
        final CompletableFuture<JsonDataType> resultHandler = new CompletableFuture<>();
        registeredEventHandlers.put(newDescriptor, resultHandler);
        this.accept(new RemoteCommand.EvalJs(newDescriptor, js));
        return resultHandler;
    }

    private void setHref(final String path) {
        Objects.requireNonNull(path);
        this.accept(new RemoteCommand.SetHref(path));
    }
}
