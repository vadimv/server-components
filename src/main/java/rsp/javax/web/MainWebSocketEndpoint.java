package rsp.javax.web;

import rsp.Component;
import rsp.page.*;
import rsp.server.DeserializeInMessage;
import rsp.server.HttpRequest;
import rsp.server.OutMessages;
import rsp.server.SerializeOutMessages;
import rsp.util.logging.Log;

import javax.websocket.*;
import javax.websocket.server.HandshakeRequest;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public final class MainWebSocketEndpoint<S> extends Endpoint {
    public static final String WS_ENDPOINT_PATH = "/bridge/web-socket/{pid}/{sid}";
    public static final String HANDSHAKE_REQUEST_PROPERTY_NAME = "handshakereq";
    private static final String LIVE_PAGE_SESSION_USER_PROPERTY_NAME = "livePage";

    private final Component<S> documentDefinition;
    private final Function<HttpRequest, CompletableFuture<S>> routing;
    private final StateToRouteDispatch<S> state2route;
    private final Map<QualifiedSessionId, PageRendering.RenderedPage<S>> renderedPages;
    private final BiFunction<String, RenderContext, RenderContext> enrich;
    private final Supplier<ScheduledExecutorService> schedulerSupplier;
    private final Log.Reporting log;

    private static final Set<QualifiedSessionId> lostSessionsIds = Collections.newSetFromMap(new WeakHashMap<>());

    public MainWebSocketEndpoint(Function<HttpRequest, CompletableFuture<S>> routing,
                                 StateToRouteDispatch<S> state2route,
                                 Map<QualifiedSessionId, PageRendering.RenderedPage<S>> renderedPages,
                                 Component<S> documentDefinition,
                                 BiFunction<String, RenderContext, RenderContext> enrich,
                                 Supplier<ScheduledExecutorService> schedulerSupplier,
                                 Log.Reporting log) {
        this.routing = routing;
        this.state2route = state2route;
        this.renderedPages = renderedPages;
        this.documentDefinition = documentDefinition;
        this.enrich = enrich;
        this.schedulerSupplier = schedulerSupplier;
        this.log = log;
    }

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        log.debug(l -> l.log("Websocket endpoint opened, session: " + session.getId()));
        final OutMessages out = new SerializeOutMessages((msg) -> sendText(session, msg));
        final HttpRequest handshakeRequest = (HttpRequest) endpointConfig.getUserProperties().get(HANDSHAKE_REQUEST_PROPERTY_NAME);
        final QualifiedSessionId qsid = new QualifiedSessionId(session.getPathParameters().get("pid"),
                                                               session.getPathParameters().get("sid"));

        final PageRendering.RenderedPage<S> page = renderedPages.get(qsid);
        if (page == null) {
            log.trace(l -> l.log("Pre-rendered page not found for SID: " + qsid));
            if (!isKnownLostSession(qsid)) {
                log.warn(l -> l.log("Reload a remote on: " + handshakeRequest.uri.getHost() + ":" + handshakeRequest.uri.getPort()));
                out.evalJs(-1, "RSP.reload()");
            }
        } else {
            renderedPages.remove(qsid);
            final LivePagePropertiesSnapshot currentPageSnapshot = new LivePagePropertiesSnapshot(page.request.path,
                                                              page.domRoot,
                                                              Map.of(),
                                                              Map.of());

            final LivePageState<S> livePageState = new LivePageState<>(currentPageSnapshot,
                                                                       qsid,
                                                                       state2route,
                                                                       documentDefinition,
                                                                       enrich,
                                                                       out);
            final LivePage<S> livePage = new LivePage<S>(qsid,
                                                         livePageState,
                                                         schedulerSupplier.get(),
                                                         out,
                                                         log);
            session.getUserProperties().put(LIVE_PAGE_SESSION_USER_PROPERTY_NAME, livePage);

            final DeserializeInMessage in = new DeserializeInMessage(livePage, log);
            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String s) {
                    log.trace(l -> l.log(session.getId() + " -> " + s));
                    in.parse(s);
                }
            });

            livePageState.accept(page.state);
            out.setRenderNum(0);

            // Invoke this page's post start events
            /*currentPageSnapshot.get().events.values().forEach(event -> { // TODO should these events to be ordered by its elements paths?
                if (POST_START_EVENT_TYPE.equals(event.eventTarget.eventType)) {
                    final EventContext eventContext = createEventContext(JsonDataType.Object.EMPTY);
                    event.eventHandler.accept(eventContext);
                }
            })*/;
            log.debug(l -> l.log("Live page started: " + this));
        }
    }

    private void sendText(Session session, String text) {
        try {
            log.trace(l -> l.log(session.getId() + " <- " + text));
            session.getBasicRemote().sendText(text);
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        shutdown(session);
        log.debug(l -> l.log("WebSocket closed " + closeReason.getReasonPhrase()));
    }

    @Override
    public void onError(Session session, Throwable thr) {
        shutdown(session);
        log.error(l -> l.log("WebSocket error: " + thr.getLocalizedMessage(), thr));
    }

    private void shutdown(Session session) {
        final LivePage<S> livePage = (LivePage<S>) session.getUserProperties().get(LIVE_PAGE_SESSION_USER_PROPERTY_NAME);
        if (livePage != null) {
            livePage.shutdown();
            log.debug(l -> l.log("Shutdown session: " + session.getId()));
        }
    }

    public static HttpRequest of(HandshakeRequest handshakeRequest) {
        return HttpRequestUtils.httpRequest(handshakeRequest);
    }

    public static boolean isKnownLostSession(QualifiedSessionId qsid) {
        synchronized (lostSessionsIds) {
            if (lostSessionsIds.contains(qsid)) {
                return true;
            }
            lostSessionsIds.add(qsid);
            return false;
        }
    }
}
