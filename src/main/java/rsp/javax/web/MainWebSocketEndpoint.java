package rsp.javax.web;

import rsp.*;
import rsp.server.DeserializeKorolevInMessage;
import rsp.server.OutMessages;
import rsp.server.SerializeKorolevOutMessages;
import rsp.services.LivePage;

import javax.websocket.*;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public class MainWebSocketEndpoint<S> extends Endpoint {
    private final Map<QualifiedSessionId, Page<S>> pagesStorage;
    private final BiFunction<String, RenderContext<S>, RenderContext<S>> enrich;

    public MainWebSocketEndpoint(Map<QualifiedSessionId,
                                 Page<S>> pagesStorage,
                                 BiFunction<String, RenderContext<S>, RenderContext<S>> enrich) {
        this.pagesStorage = pagesStorage;
        this.enrich = enrich;
    }

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        final OutMessages out = new SerializeKorolevOutMessages((msg) -> sendText(session, msg));
        final Optional<LivePage<S>> livePage = LivePage.of(pagesStorage,
                                                             session.getPathParameters(),
                                                             enrich,
                                                             out);
        livePage.ifPresent(p -> {
            final DeserializeKorolevInMessage in = new DeserializeKorolevInMessage(p);
            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String s) {
                    System.out.println(s);
                    in.parse(s);
                }
            });
        });


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
}
