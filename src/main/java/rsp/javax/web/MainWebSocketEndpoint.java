package rsp.javax.web;

import rsp.*;
import rsp.server.DeserializeKorolevInMessage;
import rsp.server.HttpRequest;
import rsp.server.OutMessages;
import rsp.server.SerializeKorolevOutMessages;
import rsp.services.LivePage;
import rsp.services.PageRendering;

import javax.websocket.*;
import javax.websocket.server.HandshakeRequest;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MainWebSocketEndpoint<S> extends Endpoint {
    public static final String HANDSHAKE_REQUEST_PROPERTY_NAME = "handshakereq";

    private final Component<S> documentDefinition;
    private final Function<HttpRequest, CompletableFuture<S>> routing;
    private final BiFunction<String, S, String> state2route;
    private final Map<QualifiedSessionId, PageRendering.RenderedPage<S>> renderedPages;
    private final BiFunction<String, RenderContext<S>, RenderContext<S>> enrich;

    public MainWebSocketEndpoint(Function<HttpRequest, CompletableFuture<S>> routing,
                                 BiFunction<String, S, String> state2route,
                                 Map<QualifiedSessionId, PageRendering.RenderedPage<S>> renderedPages,
                                 Component<S> documentDefinition,
                                 BiFunction<String, RenderContext<S>, RenderContext<S>> enrich) {
        this.routing = routing;
        this.state2route = state2route;
        this.renderedPages = renderedPages;
        this.documentDefinition = documentDefinition;
        this.enrich = enrich;
    }

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        final OutMessages out = new SerializeKorolevOutMessages((msg) -> sendText(session, msg));
        final HttpRequest handshakeRequest = (HttpRequest) endpointConfig.getUserProperties().get(HANDSHAKE_REQUEST_PROPERTY_NAME);
        final LivePage<S> livePage = LivePage.of(handshakeRequest,
                                                   new QualifiedSessionId(session.getPathParameters().get("pid"),
                                                                          session.getPathParameters().get("sid")),
                                                   routing,
                                                   state2route,
                                                   renderedPages,
                                                   documentDefinition,
                                                   enrich,
                                                   out);
        final DeserializeKorolevInMessage in = new DeserializeKorolevInMessage(livePage);
        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String s) {
                System.out.println(s);
                in.parse(s);
            }
        });
        livePage.start();
    }

    private static void sendText(Session session, String text) {
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

    public static HttpRequest of(HandshakeRequest handshakeRequest) {
        return new HttpRequest(handshakeRequest.getRequestURI().getPath(),
                name ->  Optional.ofNullable(handshakeRequest.getParameterMap().get(name)).map(val -> val.get(0)),
                name -> Optional.ofNullable(handshakeRequest.getHeaders().get(name)).map(val -> val.get(0)));
    }
}
