package rsp.page;

import rsp.*;
import rsp.dom.DomTreeRenderContext;
import rsp.dom.Tag;
import rsp.server.HttpRequest;
import rsp.server.HttpResponse;
import rsp.server.Path;
import rsp.state.ReadOnly;
import rsp.util.RandomString;
import rsp.util.data.Tuple2;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class PageRendering<S> {
    public static final int KEY_LENGTH = 64;
    public static final String DEVICE_ID_COOKIE_NAME = "deviceId";

    private final RandomString randomStringGenerator = new RandomString(KEY_LENGTH);

    private final Component<S> documentDefinition;
    private final Function<HttpRequest, CompletableFuture<S>> routing;
    private final Map<QualifiedSessionId, RenderedPage<S>> renderedPages;
    private final BiFunction<String, RenderContext, RenderContext> enrich;

    public PageRendering(Function<HttpRequest, CompletableFuture<S>> routing,
                         Map<QualifiedSessionId, RenderedPage<S>> pagesStorage,
                         Component<S> documentDefinition,
                         BiFunction<String, RenderContext, RenderContext> enrich) {
        this.routing = routing;
        this.renderedPages = pagesStorage;
        this.documentDefinition = documentDefinition;
        this.enrich = enrich;
    }

    public CompletableFuture<HttpResponse> httpGet(HttpRequest request) {
        if (request.path.endsWith("favicon.ico")) {
            return CompletableFuture.completedFuture(new HttpResponse(404, Collections.emptyList(), "No favicon.ico"));
        } else if (request.path.startsWith("static")) {
            return staticFileResponse(request.path);
        } else {
            return rspResponse(request);
        }
    }

    private CompletableFuture<HttpResponse> staticFileResponse(Path path) {
            return path.last().flatMap(fileName -> {
                final URL fileUrl =  this.getClass().getResource("/" + fileName);
                if (fileUrl != null) {
                    try {
                        return Optional.of(CompletableFuture.completedFuture(new HttpResponse(200,
                                                                                              Collections.emptyList(),
                                                                                              fileUrl.openStream())));
                    } catch (IOException e) {
                        return Optional.of(CompletableFuture.completedFuture(new HttpResponse(500,
                                            Collections.emptyList(),
                                            "Exception on loading a static resource: "
                                                    + path
                                                    + " " + e.getMessage())));
                    }
                } else {
                    return Optional.empty();
                }
            }).orElse(CompletableFuture.completedFuture(new HttpResponse(404,
                                                                            Collections.emptyList(),
                                                                            "Resource not found: " + path)));

    }

    private CompletableFuture<HttpResponse> rspResponse(HttpRequest request) {
        try {
            return routing.apply(request).thenApply(initialState -> {
                final String deviceId = request.getCookie().apply(DEVICE_ID_COOKIE_NAME).orElse(randomStringGenerator.newString());
                final String sessionId = randomStringGenerator.newString();
                final QualifiedSessionId pageId = new QualifiedSessionId(deviceId, sessionId);

                final DomTreeRenderContext domTreeContext = new DomTreeRenderContext();
                documentDefinition.render(new ReadOnly<>(initialState)).accept(enrich.apply(sessionId, domTreeContext));

                renderedPages.put(pageId, new RenderedPage<>(request, initialState, domTreeContext.root()));

                return new HttpResponse(domTreeContext.statusCode(),
                                        headers(deviceId),
                                        domTreeContext.toString());
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
        public final HttpRequest request;
        public final S state;
        public final Tag domRoot;

        public RenderedPage(HttpRequest request,
                            S initialState,
                            Tag domRoot) {
            this.request = request;
            this.state = initialState;
            this.domRoot = domRoot;
        }
    }

}
