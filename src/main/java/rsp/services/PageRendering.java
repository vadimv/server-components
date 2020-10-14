package rsp.services;

import rsp.*;
import rsp.dom.DomTreeRenderContext;
import rsp.dom.Tag;
import rsp.server.HttpRequest;
import rsp.server.HttpResponse;
import rsp.state.ReadOnly;
import rsp.util.RandomString;
import rsp.util.Tuple2;

import java.io.IOException;
import java.net.URL;
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
    private final Map<QualifiedSessionId, RenderedPage<S>> renderedPages;
    private final BiFunction<String, RenderContext<S>, RenderContext<S>> enrich;

    public PageRendering(Function<HttpRequest, CompletableFuture<S>> routing,
                         BiFunction<String, S, String> state2route,
                         Map<QualifiedSessionId, RenderedPage<S>> pagesStorage,
                         Component<S> documentDefinition,
                         BiFunction<String, RenderContext<S>, RenderContext<S>> enrich) {
        this.routing = routing;
        this.state2route = state2route;
        this.renderedPages = pagesStorage;
        this.documentDefinition = documentDefinition;
        this.enrich = enrich;
    }

    public CompletableFuture<HttpResponse> httpGet(HttpRequest request) {
        if (request.path.endsWith("favicon.ico")) {
            return CompletableFuture.completedFuture(new HttpResponse(404, Collections.EMPTY_LIST, "No favicon.ico"));
        } else if (request.path.startsWith("/static/")) {
            return staticFileResponse(request);
        } else {
            return rspResponse(request);
        }
    }

    private CompletableFuture<HttpResponse> staticFileResponse(HttpRequest request) {
        final String[] pathTokens = request.path.split("/");
        if (pathTokens.length > 2) {
            final String fileName = pathTokens[pathTokens.length - 1];

            final URL fileUrl =  this.getClass().getResource("/" + fileName);
            if (fileUrl != null) {
                try {
                    return CompletableFuture.completedFuture(new HttpResponse(200,
                                                                               Collections.emptyList(),
                                                                               fileUrl.openStream()));
                    } catch (IOException e) {
                        return CompletableFuture.completedFuture(new HttpResponse(500,
                                                                                    Collections.EMPTY_LIST,
                                                                                    "Exception on loading a static resource: "
                                                                                            + request.path
                                                                                            + " " + e.getMessage()));
                    }
            }
        }
        return CompletableFuture.completedFuture(new HttpResponse(404,
                                                                   Collections.EMPTY_LIST,
                                                             "Resource not found: " + request.path));
    }

    private CompletableFuture<HttpResponse> rspResponse(HttpRequest request) {
        try {
            return routing.apply(request).thenApply(initialState -> {
                final String deviceId = request.getCookie().apply(DEVICE_ID_COOKIE_NAME).orElse(randomStringGenerator.newString());
                final String sessionId = randomStringGenerator.newString();
                final QualifiedSessionId pageId = new QualifiedSessionId(deviceId, sessionId);

                final XhtmlRenderContext<S> newCtx = new XhtmlRenderContext<>(TextPrettyPrinting.NO_PRETTY_PRINTING, "<!DOCTYPE html>");
                final DomTreeRenderContext<S> domTreeContext = new DomTreeRenderContext<>();
                documentDefinition.of(new ReadOnly<>(initialState)).accept(new DelegatingRenderContext(enrich.apply(sessionId, newCtx),
                                                                                                                enrich.apply(sessionId, domTreeContext)));

                renderedPages.put(pageId, new RenderedPage<S>(initialState, domTreeContext.root));

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

    public static class RenderedPage<S> {
        public final S state;
        public final Tag domRoot;

        public RenderedPage(S initialState,
                            Tag domRoot) {
            this.state = initialState;
            this.domRoot = domRoot;
        }
    }

}
