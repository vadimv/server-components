package rsp.javax.web;

import rsp.*;
import rsp.dom.*;
import rsp.state.UseState;

import javax.websocket.*;
import java.io.IOException;
import java.util.Map;

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
        final DomTreeRenderContext<S> context = new DomTreeRenderContext<>();
        final EnrichingXhtmlContext<S> enrichingContext = new EnrichingXhtmlContext<>(context,
                qsid.sessionId,
                "/",
                DefaultConnectionLostWidget.HTML,
                5000);
        page.rootComponent.materialize(useState).accept(enrichingContext);
        session.getUserProperties().put("current-dom", context.root);
        sendText(session, "[0,0]");

        // Register event types on client
        context.events.entrySet().stream().map(e -> e.getValue().eventType).distinct().forEach(eventType -> {
            sendText( session,"[2,\"" + eventType + "\",false]");
        });

        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String s) {
                System.out.println(s);
                final String eventType = s.startsWith("[6") ? "heartBeat" : "click";
                if(eventType.equals("heartBeat")) {
                    return;
                }

                Path eventElementPath = eventElementPath(s);
                while(eventElementPath.level() > 0) {
                    Event event = context.events.get(eventElementPath);
                    if(event != null && event.eventType.equals(eventType)) {
                        final EventContext eventContext = new EventContext();
                        event.eventHandler.accept(eventContext);
                        break;
                    } else {
                        eventElementPath = eventElementPath.parent().get();
                    }
                }
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

    private Path eventElementPath(String s) {
        return Path.of(s.split("\"")[1].split(":")[1]);
    }

    public void onClose(Session session, CloseReason closeReason) {
        System.out.println("Closed " + closeReason.getReasonPhrase());
    }

    public void onError(Session session, Throwable thr) {
        System.out.println("Error:" + thr.getLocalizedMessage());
        thr.printStackTrace();
    }


}
