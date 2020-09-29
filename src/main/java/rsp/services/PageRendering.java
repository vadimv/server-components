package rsp.services;

import rsp.*;
import rsp.dom.DomTreeRenderContext;
import rsp.server.HttpRequest;
import rsp.server.HttpResponse;
import rsp.state.ReadOnly;
import rsp.util.RandomString;
import rsp.util.Tuple2;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

    public CompletableFuture<HttpResponse> httpGet(HttpRequest request) {
        if(request.path.contains("favicon.ico")) {
            return CompletableFuture.completedFuture(new HttpResponse(404, Collections.EMPTY_LIST, "No favicon.ico"));
        }
        try {
            return routing.apply(request).thenApply(initialState -> {
                final String deviceId = request.getCookie.apply(DEVICE_ID_COOKIE_NAME).orElse(randomStringGenerator.newString());
                final String sessionId = randomStringGenerator.newString();
                final QualifiedSessionId pageId = new QualifiedSessionId(deviceId, sessionId);

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

                return new HttpResponse(200,
                                        headers(deviceId),
                                        newCtx.toString());
            });
        } catch (Throwable ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    private List<Tuple2<String,String>> headers(String deviceId) {
        return List.of(new Tuple2<>("content-type", "text/html; charset=utf-8"),
                       new Tuple2<>("cache-control", "no-store, no-cache, must-revalidate"),
                       new Tuple2<>("Set-Cookie", String.format("%s=%s; Path=%s; Max-Age=%d; SameSite=Lax",
                                                                DEVICE_ID_COOKIE_NAME,
                                                                deviceId,
                                                                "/",
                                                                60 * 60 * 24 * 365 * 10 /* 10 years */ )));

    }

}
