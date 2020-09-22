package rsp.services;

import rsp.dom.DomTreeRenderContext;
import rsp.server.HttpRequest;
import rsp.server.HttpResponse;
import rsp.state.MutableState;
import rsp.state.ReadOnly;
import rsp.util.RandomString;
import rsp.util.Tuple2;
import rsp.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Function;

public class PageRendering<S> {
    public static final int KEY_LENGTH = 64;
    public static final String DEVICE_ID_COOKIE_NAME = "deviceId";

    private final RandomString randomStringGenerator = new RandomString(KEY_LENGTH);

    private final Component<S> documentDefinition;
    private final Function<HttpRequest, CompletableFuture<S>> routing;
    private final BiFunction<String, S, String> state2route;

    public final Map<QualifiedSessionId, Page<S>> pagesStorage;

    public PageRendering(Function<HttpRequest, CompletableFuture<S>> routing,
                         BiFunction<String, S, String> state2route,
                         Map<QualifiedSessionId, Page<S>> pagesStorage,
                         Component<S> documentDefinition) {
        this.routing = routing;
        this.state2route = state2route;
        this.pagesStorage = pagesStorage;
        this.documentDefinition = documentDefinition;
    }

    public HttpResponse httpGet(HttpRequest request) {
        final String deviceId = request.getCookie.apply(DEVICE_ID_COOKIE_NAME).orElse(randomStringGenerator.newString());
        final String sessionId = randomStringGenerator.newString();
        final QualifiedSessionId pageId = new QualifiedSessionId(deviceId, sessionId);
        final S initialState = routing.apply(request).join();


        final XhtmlRenderContext<S> newCtx = new XhtmlRenderContext<>(TextPrettyPrinting.NO_PRETTY_PRINTING, "<!DOCTYPE html>");
        final EnrichingXhtmlContext<S> enrichingContext = new EnrichingXhtmlContext<>(newCtx,
                                                                                        sessionId,
                                                                                        "/",
                                                                                        DefaultConnectionLostWidget.HTML,
                                                                                        5000);
        final DomTreeRenderContext<S> domTreeContext = new DomTreeRenderContext<>();
        documentDefinition.materialize(new ReadOnly<>(initialState)).accept(new DelegatingRenderContext(enrichingContext, domTreeContext));


        pagesStorage.put(pageId, new Page<S>(request.path,
                                             documentDefinition,
                                             initialState,
                                             state2route,
                                             domTreeContext.root,
                                             domTreeContext.events));

        final List<Tuple2<String,String>> headers = List.of(new Tuple2<>("Content-Type", "text/html"),
                                                            HttpResponse.Headers.setCookie(DEVICE_ID_COOKIE_NAME,
                                                                                           deviceId,
                                                                                           "/",
                                                                                         60 * 60 * 24 * 365 * 10 /* 10 years */ ));
        return  new HttpResponse(200,
                                headers,
                                newCtx.toString());
    }

    public RenderContext<S> renderComponents(S state, String sessionId) {
        final XhtmlRenderContext<S> newCtx = new XhtmlRenderContext<>(TextPrettyPrinting.NO_PRETTY_PRINTING, "<!DOCTYPE html>");
        final EnrichingXhtmlContext<S> enrichingContext = new EnrichingXhtmlContext<>(newCtx,
                                                                sessionId,
                                                           "/",
                                                                DefaultConnectionLostWidget.HTML,
                                                   5000);
        documentDefinition.materialize(new MutableState<>(state)).accept(enrichingContext);
        return enrichingContext;
    }

    private static String stateSessionKey(String deviceId, String sessionId) {
        return deviceId + "-" + sessionId;
    }

}
