package rsp.page;

import rsp.*;
import rsp.dom.DomTreePageRenderContext;
import rsp.dom.Tag;
import rsp.server.HttpRequest;
import rsp.server.HttpResponse;
import rsp.server.Path;
import rsp.state.ReadOnly;
import rsp.util.RandomString;
import rsp.util.data.Tuple2;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class PageRendering<S> {
    public static final int KEY_LENGTH = 64;
    public static final String DEVICE_ID_COOKIE_NAME = "deviceId";

    private final RandomString randomStringGenerator = new RandomString(KEY_LENGTH);

    private final Component<S> documentDefinition;
    private final Function<HttpRequest, Optional<CompletableFuture<S>>> routing;
    private final Map<QualifiedSessionId, RenderedPage<S>> renderedPages;
    private final BiFunction<String, PageRenderContext, PageRenderContext> enrich;

    public PageRendering(Function<HttpRequest, Optional<CompletableFuture<S>>> routing,
                         Map<QualifiedSessionId, RenderedPage<S>> pagesStorage,
                         Component<S> documentDefinition,
                         BiFunction<String, PageRenderContext, PageRenderContext> enrich) {
        this.routing = routing;
        this.renderedPages = pagesStorage;
        this.documentDefinition = documentDefinition;
        this.enrich = enrich;
    }

    public CompletableFuture<HttpResponse> httpResponse(HttpRequest request) {
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
            final String deviceId = request.cookie(DEVICE_ID_COOKIE_NAME).orElse(randomStringGenerator.newString());
            final String sessionId = randomStringGenerator.newString();
            final QualifiedSessionId pageId = new QualifiedSessionId(deviceId, sessionId);
            renderedPages.get(pageId);
            final var route = routing.apply(request);
            return route.isPresent() ? route.get().thenApply(initialState -> {
                final DomTreePageRenderContext domTreeContext = new DomTreePageRenderContext();
                documentDefinition.render(new ReadOnly<>(initialState)).accept(enrich.apply(sessionId, domTreeContext));
                renderedPages.put(pageId, new RenderedPage<>(request, initialState, domTreeContext.root()));
                return new HttpResponse(domTreeContext.statusCode(),
                                        headers(domTreeContext.headers(), deviceId),
                                        domTreeContext.toString());
            }) : defaultPage404();
        } catch (Throwable ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    private CompletableFuture<HttpResponse> defaultPage404() {
        return CompletableFuture.completedFuture(new HttpResponse(404, List.of(),"Not found"));
    }

    private List<Tuple2<String,String>> headers(Map<String, String> headers, String deviceId) {
        final List<Tuple2<String,String>> resultHeaders = new ArrayList<>();
        for (Map.Entry<String, String> entry : headers.entrySet() ) {
            resultHeaders.add(new Tuple2<>(entry.getKey(), entry.getValue()));
        }
        resultHeaders.add(new Tuple2<>("content-type", "text/html; charset=utf-8"));
        resultHeaders.add(new Tuple2<>("cache-control", "no-store, no-cache, must-revalidate"));
        resultHeaders.add(new Tuple2<>("Set-Cookie", String.format("%s=%s; Path=%s; Max-Age=%d; SameSite=Lax",
                                                                DEVICE_ID_COOKIE_NAME,
                                                                deviceId,
                                                                "/",
                                                                60 * 60 * 24 * 365 * 10 /* 10 years */ )));
        return resultHeaders;

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
