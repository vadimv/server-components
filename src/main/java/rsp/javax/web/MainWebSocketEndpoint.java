package rsp.javax.web;

import rsp.component.Component;
import rsp.page.*;
import rsp.server.*;
import rsp.server.http.HttpRequest;
import rsp.server.protocol.RemotePageMessageDecoder;
import rsp.server.protocol.RemotePageMessageEncoder;
import rsp.util.json.JsonParser;
import rsp.util.json.JsonSimpleUtils;

import javax.websocket.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import static java.lang.System.Logger.Level.*;

public final class MainWebSocketEndpoint<S> extends Endpoint {
    private static final System.Logger logger = System.getLogger(MainWebSocketEndpoint.class.getName());

    public static final String WS_ENDPOINT_PATH = "/bridge/web-socket/{pid}/{sid}";
    public static final String HANDSHAKE_REQUEST_PROPERTY_NAME = "handshakereq";
    private static final String LIVE_PAGE_SESSION_USER_PROPERTY_NAME = "livePage";

    private final Path basePath;
    private final Map<QualifiedSessionId, RenderedPage<S>> renderedPages;
    private final Supplier<ScheduledExecutorService> schedulerSupplier;
    private final PageLifeCycle<S> lifeCycleEventsListener;

    private final JsonParser jsonParser = JsonSimpleUtils.createParser();

    private static final Set<QualifiedSessionId> lostSessionsIds = Collections.newSetFromMap(new WeakHashMap<>());

    public MainWebSocketEndpoint(final Path basePath,
                                 final Map<QualifiedSessionId, RenderedPage<S>> renderedPages,
                                 final Supplier<ScheduledExecutorService> schedulerSupplier,
                                 final PageLifeCycle<S> lifeCycleEventsListener) {
        this.basePath = Objects.requireNonNull(basePath);
        this.renderedPages = Objects.requireNonNull(renderedPages);
        this.schedulerSupplier = Objects.requireNonNull(schedulerSupplier);
        this.lifeCycleEventsListener = Objects.requireNonNull(lifeCycleEventsListener);
    }

    @Override
    public void onOpen(final Session session, final EndpointConfig endpointConfig) {
        logger.log(DEBUG, () -> "Websocket endpoint opened, session: " + session.getId());
        final RemoteOut remoteOut = new RemotePageMessageEncoder((msg) -> sendText(session, msg));
        final HttpRequest handshakeRequest = (HttpRequest) endpointConfig.getUserProperties().get(HANDSHAKE_REQUEST_PROPERTY_NAME);
        final QualifiedSessionId qsid = new QualifiedSessionId(session.getPathParameters().get("pid"),
                                                               session.getPathParameters().get("sid"));

        final RenderedPage<S> renderedPage = renderedPages.remove(qsid);

        if (renderedPage == null) {
            logger.log(TRACE, () -> "Pre-rendered page not found for SID: " + qsid);
            if (!isKnownLostSession(qsid)) {
                logger.log(WARNING, () -> "Reload a remote on: " + handshakeRequest.uri.getHost() + ":" + handshakeRequest.uri.getPort());
                remoteOut.evalJs(-1, "RSP.reload()");
            }
        } else {
            renderedPage.remoteOutReference.set(remoteOut);
            final Component<?, S> rootComponent = renderedPage.rootComponent;
            final LivePageSession livePage = new LivePageSession(qsid,
                                                                 basePath,
                                                                 renderedPage.stateOriginLookup,
                                                                 new Schedules(schedulerSupplier.get()),
                                                                 rootComponent,
                                                                 remoteOut);
            session.getUserProperties().put(LIVE_PAGE_SESSION_USER_PROPERTY_NAME, livePage);

            final RemotePageMessageDecoder in = new RemotePageMessageDecoder(jsonParser, livePage);
            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(final String s) {
                    logger.log(TRACE, () -> session.getId() + " -> " + s);
                    in.decode(s);
                }
            });

            remoteOut.setRenderNum(0);
            livePage.init();
            lifeCycleEventsListener.pageCreated(qsid, rootComponent.getState(), rootComponent);
            logger.log(DEBUG, () -> "Live page started: " + this);
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
            livePage.shutdown();
            lifeCycleEventsListener.pageClosed(livePage.getId());
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
