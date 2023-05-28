package rsp.javax.web;

import rsp.page.*;
import rsp.server.DeserializeInMessage;
import rsp.server.HttpRequest;
import rsp.server.OutMessages;
import rsp.server.SerializeOutMessages;

import javax.websocket.*;
import javax.websocket.server.HandshakeRequest;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.lang.System.Logger.Level.*;

public final class MainWebSocketEndpoint<S> extends Endpoint {
    private static final System.Logger logger = System.getLogger(MainWebSocketEndpoint.class.getName());

    public static final String WS_ENDPOINT_PATH = "/bridge/web-socket/{pid}/{sid}";
    public static final String HANDSHAKE_REQUEST_PROPERTY_NAME = "handshakereq";
    private static final String LIVE_PAGE_SESSION_USER_PROPERTY_NAME = "livePage";

    private final StateToRouteDispatch<S> state2route;
    private final Map<QualifiedSessionId, LivePageSnapshot<S>> renderedPages;
    private final BiFunction<String, PageRenderContext, PageRenderContext> enrich;
    private final Supplier<ScheduledExecutorService> schedulerSupplier;
    private final PageLifeCycle<S> lifeCycleEventsListener;


    private static final Set<QualifiedSessionId> lostSessionsIds = Collections.newSetFromMap(new WeakHashMap<>());

    public MainWebSocketEndpoint(final StateToRouteDispatch<S> state2route,
                                 final Map<QualifiedSessionId, LivePageSnapshot<S>> renderedPages,
                                 final BiFunction<String, PageRenderContext, PageRenderContext> enrich,
                                 final Supplier<ScheduledExecutorService> schedulerSupplier,
                                 final PageLifeCycle<S> lifeCycleEventsListener) {
        this.state2route = state2route;
        this.renderedPages = renderedPages;
        this.enrich = enrich;
        this.schedulerSupplier = schedulerSupplier;
        this.lifeCycleEventsListener = lifeCycleEventsListener;
    }

    @Override
    public void onOpen(final Session session, final EndpointConfig endpointConfig) {
        logger.log(DEBUG, () -> "Websocket endpoint opened, session: " + session.getId());
        final OutMessages out = new SerializeOutMessages((msg) -> sendText(session, msg));
        final HttpRequest handshakeRequest = (HttpRequest) endpointConfig.getUserProperties().get(HANDSHAKE_REQUEST_PROPERTY_NAME);
        final QualifiedSessionId qsid = new QualifiedSessionId(session.getPathParameters().get("pid"),
                                                               session.getPathParameters().get("sid"));

        final LivePageSnapshot<S> currentPageSnapshot = renderedPages.remove(qsid);
        if (currentPageSnapshot == null) {
            logger.log(TRACE, () -> "Pre-rendered page not found for SID: " + qsid);
            if (!isKnownLostSession(qsid)) {
                logger.log(WARNING, () -> "Reload a remote on: " + handshakeRequest.uri.getHost() + ":" + handshakeRequest.uri.getPort());
                out.evalJs(-1, "RSP.reload()");
            }
        } else {
            final LivePageState<S> livePageState = new LivePageState<>(qsid,
                                                                       currentPageSnapshot,
                                                                       state2route,
                                                                       enrich,
                                                                       out);
            currentPageSnapshot.componentsStateNotificationListener.setListener(livePageState);
            //lifeCycleEventsListener.beforeLivePageCreated(qsid, livePageState);
            final LivePage<S> livePage = new LivePage<>(qsid,
                                                        livePageState,
                                                        schedulerSupplier.get(),
                                                        out);
            session.getUserProperties().put(LIVE_PAGE_SESSION_USER_PROPERTY_NAME, livePage);

            final DeserializeInMessage in = new DeserializeInMessage(livePage);
            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(final String s) {
                    logger.log(TRACE, () -> session.getId() + " -> " + s);
                    in.parse(s);
                }
            });

            out.setRenderNum(0);
            out.listenEvents(currentPageSnapshot.events.values().stream().collect(Collectors.toList()));
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
        logger.log(ERROR, () -> "WebSocket error: " + thr.getLocalizedMessage(), thr);
    }

    private void shutdown(final Session session) {
        @SuppressWarnings("unchecked")
        final LivePage<S> livePage = (LivePage<S>) session.getUserProperties().get(LIVE_PAGE_SESSION_USER_PROPERTY_NAME);
        if (livePage != null) {
            livePage.shutdown();
            //lifeCycleEventsListener.afterLivePageClosed(livePage.qsid, livePage.getPageState());
            logger.log(DEBUG, () -> "Shutdown session: " + session.getId());
        }
    }

    public static HttpRequest of(final HandshakeRequest handshakeRequest) {
        return HttpRequestUtils.httpRequest(handshakeRequest);
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
