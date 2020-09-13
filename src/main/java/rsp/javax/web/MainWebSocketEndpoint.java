package rsp.javax.web;

import rsp.*;
import rsp.dom.*;
import rsp.server.InMessage;
import rsp.server.ParseInMessage;
import rsp.state.UseState;

import javax.websocket.*;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class MainWebSocketEndpoint<S> extends Endpoint {
    private final Map<QualifiedSessionId, Page<S>> pagesStorage;
    public MainWebSocketEndpoint(Map<QualifiedSessionId, Page<S>> pagesStorage) {
        this.pagesStorage = pagesStorage;
    }

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        System.out.println("WebSocket open " + session.getRequestURI());
        final QualifiedSessionId qsid = new QualifiedSessionId(session.getPathParameters().get("pid"), session.getPathParameters().get("sid"));
        final Page<S> page = pagesStorage.get(qsid);

        if(page == null) {
            // a connection after server's restart
            sendText( session,"[10,\"descriptor\",\"location.reload()\"]");
            return;
        } else {
            pagesStorage.remove(qsid);
            session.getUserProperties().put("current-dom", page.domRoot);
            session.getUserProperties().put("root-component", page.rootComponent);
            session.getUserProperties().put("path", page.path);
        };
        final UseState<S> useState = new UseState<S>() {
            private volatile S state = page.initialState;
            @Override
            public void accept(S newState) {
                final String newRoute = page.state2route.apply(page.path, newState);
                if(newRoute.equals(page.path)) {
                    state = newState;

                    final DomTreeRenderContext<S> newContext = new DomTreeRenderContext<>();
                    final EnrichingXhtmlContext<S> newEnrichingContext = new EnrichingXhtmlContext<>(newContext,
                            qsid.sessionId,
                            "/",
                            DefaultConnectionLostWidget.HTML,
                            5000);
                    final Component<S> root = (Component<S>) session.getUserProperties().get("root-component");
                    root.materialize(this).accept(newEnrichingContext);

                    // calculate diff between currentContext and newContext
                    final var currentRoot = (Tag) session.getUserProperties().get("current-dom");
                    final var remoteChangePerformer = new RemoteDomChangesPerformer();
                    new Diff(currentRoot, newContext.root, remoteChangePerformer).run();
                    remoteChangePerformer.commandsString().ifPresent(commands -> {
                        sendText(session, commands);
                    });

                    session.getUserProperties().put("current-dom", newContext.root);

                } else {
                    // send new URL/route command
                    System.out.println("New route: " + newRoute);
                }
            }

            @Override
            public S get() {
                return state;
            }
        };
        final DomTreeRenderContext<S> domTreeRenderContext = new DomTreeRenderContext<>();
        final Map<String, CompletableFuture<?>> registeredEventHandlers = new ConcurrentHashMap<>();
        final EnrichingXhtmlContext<S> enrichingDomTreeRenderContext = new EnrichingXhtmlContext<>(domTreeRenderContext,
                qsid.sessionId,
                "/",
                DefaultConnectionLostWidget.HTML,
                5000);
        page.rootComponent.materialize(useState).accept(enrichingDomTreeRenderContext);
        session.getUserProperties().put("current-dom", domTreeRenderContext.root);
        sendText(session, "[0,0]");

        // Register event types on client
        domTreeRenderContext.events.entrySet().stream().map(e -> e.getValue().eventType).distinct().forEach(eventType -> {
            sendText( session,"[2,\"" + eventType + "\",false]");
        });

        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String s) {
                System.out.println(s);
                ParseInMessage.parse(s).ifPresent(message -> {
                    if (message instanceof InMessage.DomEventInMessage) {
                        final InMessage.DomEventInMessage domEvent = (InMessage.DomEventInMessage) message;
                        Path eventElementPath = domEvent.path;
                        while(domEvent.path.level() > 0) {
                            Event event = domTreeRenderContext.events.get(eventElementPath);
                            if(event != null && event.eventType.equals(domEvent.eventType)) {
                                final EventContext eventContext = new EventContext(registeredEventHandlers);
                                event.eventHandler.accept(eventContext);
                                break;
                            } else {
                                eventElementPath = eventElementPath.parent().get();
                            }
                        }
                    } else if (message instanceof InMessage.ExtractPropertyResponseInMessage) {
                        final InMessage.ExtractPropertyResponseInMessage propertyMessage = (InMessage.ExtractPropertyResponseInMessage) message;

                    } else if (message instanceof InMessage.HeartBeat) {
                        // no-op
                    }
                });
            }
        });
    }

    private final void sendText(Session session, String text) {
        try {
            session.getBasicRemote().sendText(text);
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }
    }

    public void onClose(Session session, CloseReason closeReason) {
        System.out.println("Closed: " + closeReason.getReasonPhrase());
    }

    public void onError(Session session, Throwable thr) {
        System.out.println("Error:" + thr.getLocalizedMessage());
        thr.printStackTrace();
    }
}
