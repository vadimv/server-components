package rsp.javax.web;

import rsp.page.LivePageSession;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderedPage;
import rsp.page.events.InitSessionEvent;
import rsp.page.events.RemoteCommand;
import rsp.page.events.ShutdownSessionEvent;
import rsp.server.RemoteOut;
import rsp.server.http.HttpRequest;
import rsp.server.protocol.RemotePageMessageDecoder;
import rsp.server.protocol.RemotePageMessageEncoder;
import rsp.util.json.JsonParser;
import rsp.util.json.JsonSimpleUtils;

import javax.websocket.*;
import java.io.IOException;
import java.util.*;

import static java.lang.System.Logger.Level.*;

public final class MainWebSocketEndpoint extends Endpoint {
    private static final System.Logger logger = System.getLogger(MainWebSocketEndpoint.class.getName());

    public static final String WS_ENDPOINT_PATH = "/bridge/web-socket/{pid}/{sid}";
    public static final String HANDSHAKE_REQUEST_PROPERTY_NAME = "handshakereq";
    private static final String LIVE_PAGE_SESSION_USER_PROPERTY_NAME = "livePage";

    private final Map<QualifiedSessionId, RenderedPage> renderedPages;

    private final JsonParser jsonParser = JsonSimpleUtils.createParser();

    private static final Set<QualifiedSessionId> lostSessionsIds = Collections.newSetFromMap(new WeakHashMap<>());

    public MainWebSocketEndpoint(final Map<QualifiedSessionId, RenderedPage> renderedPages) {
        this.renderedPages = Objects.requireNonNull(renderedPages);
    }

    @Override
    public void onOpen(final Session session, final EndpointConfig endpointConfig) {
        logger.log(DEBUG, () -> "Websocket endpoint opened, session: " + session.getId());

        final HttpRequest handshakeRequest = (HttpRequest) endpointConfig.getUserProperties().get(HANDSHAKE_REQUEST_PROPERTY_NAME);
        final QualifiedSessionId qsid = new QualifiedSessionId(session.getPathParameters().get("pid"),
                                                               session.getPathParameters().get("sid"));

        final RemoteOut remoteOut = new RemotePageMessageEncoder(msg -> sendText(session, msg));
        final RenderedPage renderedPage = renderedPages.remove(qsid);
        if (renderedPage == null) {
            logger.log(TRACE, () -> "Pre-rendered page not found for SID: " + qsid);
            if (!isKnownLostSession(qsid)) {
                logger.log(WARNING, () -> "Reload a remote on: " + handshakeRequest.uri.getHost() + ":" + handshakeRequest.uri.getPort());
                remoteOut.evalJs(-1, "RSP.reload()");
            }
        } else {

            final LivePageSession livePage = new LivePageSession();
            livePage.eventsConsumer().accept(new InitSessionEvent(renderedPage.pageRenderContext,
                                                                  renderedPage.commandsBuffer,
                                                                  remoteOut));
            session.getUserProperties().put(LIVE_PAGE_SESSION_USER_PROPERTY_NAME, livePage);

            final RemotePageMessageDecoder in = new RemotePageMessageDecoder(jsonParser, livePage.eventsConsumer());
            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(final String s) {
                    logger.log(TRACE, () -> session.getId() + " -> " + s);
                    in.decode(s);
                }
            });
            remoteOut.setRenderNum(0);
            livePage.start();
            logger.log(DEBUG, () -> "Live page session started: " + this);
        }
    }

    private void sendText(final Session session, final String text) {
        try {
            logger.log(TRACE, () -> session.getId() + " <- " + text);
            session.getBasicRemote().sendText(text);
        } catch (final IOException ioException) {
            throw new RuntimeException(ioException);
        }
    }

    @Override
    public void onClose(final Session session, final CloseReason closeReason) {
        shutdown(session);
        logger.log(DEBUG, () -> "WebSocket closed " + closeReason.getReasonPhrase());
    }

    @Override
    public void onError(final Session session, final Throwable thr) {
        shutdown(session);
        logger.log(ERROR, () -> "WebSocket error: " + thr.getMessage(), thr);
    }

    private void shutdown(final Session session) {
        final LivePageSession livePage = (LivePageSession) session.getUserProperties().get(LIVE_PAGE_SESSION_USER_PROPERTY_NAME);
        if (livePage != null) {
            livePage.eventsConsumer().accept(new ShutdownSessionEvent());
            logger.log(DEBUG, () -> "Shutdown session: " + session.getId());
        }
    }

    public static boolean isKnownLostSession(final QualifiedSessionId qsid) {
        synchronized (lostSessionsIds) {
            if (lostSessionsIds.contains(qsid)) {
                return true;
            }
            lostSessionsIds.add(qsid);
            return false;
        }
    }
}
