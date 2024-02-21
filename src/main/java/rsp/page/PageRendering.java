package rsp.page;

import rsp.component.HttpRequestStatefulComponentDefinition;
import rsp.component.ComponentRenderContext;
import rsp.dom.VirtualDomPath;
import rsp.server.RemoteOut;
import rsp.server.http.*;
import rsp.server.Path;
import rsp.util.RandomString;
import rsp.util.data.Tuple2;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.System.Logger.Level.TRACE;

public final class PageRendering<S> {
    private static final System.Logger logger = System.getLogger(PageRendering.class.getName());

    public static final int KEY_LENGTH = 64;
    public static final String DEVICE_ID_COOKIE_NAME = "deviceId";

    private final RandomString randomStringGenerator = new RandomString(KEY_LENGTH);

    private final Path baseUrlPath;
    private final Map<QualifiedSessionId, RenderedPage<S>> renderedPages;
    private final HttpRequestStatefulComponentDefinition<S> rootComponentDefinition;
    private final int heartBeatIntervalMs;

    public PageRendering(final Path baseUrlPath,
                         final Map<QualifiedSessionId, RenderedPage<S>> pagesStorage,
                         final HttpRequestStatefulComponentDefinition<S> rootComponentDefinition,
                         final int heartBeatIntervalMs) {

        this.baseUrlPath = Objects.requireNonNull(baseUrlPath);
        this.renderedPages = Objects.requireNonNull(pagesStorage);
        this.rootComponentDefinition = Objects.requireNonNull(rootComponentDefinition);
        this.heartBeatIntervalMs = heartBeatIntervalMs;
    }

    public CompletableFuture<HttpResponse> httpResponse(final HttpRequest request) {
        if (request.path.endsWith("favicon.ico")) {
            return CompletableFuture.completedFuture(new HttpResponse(404, Collections.emptyList(), "No favicon.ico"));
        } else if (request.path.startsWith("static")) {
            return staticFileResponse(request.path);
        } else {
            return rspResponse(request);
        }
    }

    private CompletableFuture<HttpResponse> staticFileResponse(final Path path) {
            return path.last().flatMap(fileName -> {
                final URL fileUrl =  this.getClass().getResource("/" + fileName);
                if (fileUrl != null) {
                    try {
                        return Optional.of(CompletableFuture.completedFuture(new HttpResponse(200,
                                                                                              Collections.emptyList(),
                                                                                              fileUrl.openStream())));
                    } catch (final IOException e) {
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

    private CompletableFuture<HttpResponse> rspResponse(final HttpRequest request) {
        try {
            final String deviceId = request.cookie(DEVICE_ID_COOKIE_NAME).orElse(randomStringGenerator.newString());
            final String sessionId = randomStringGenerator.newString();
            final QualifiedSessionId pageId = new QualifiedSessionId(deviceId, sessionId);

            final AtomicReference<RemoteOut> remoteOutReference = new AtomicReference<>();
            final HttpStateOriginLookup httpStateOriginLookup = new HttpStateOriginLookup(new HttpStateOrigin(request,
                                                                                                              RelativeUrl.of(request)));
            final PageConfigScript pageConfigScript = new PageConfigScript(sessionId,
                                                                          "/",
                                                                           DefaultConnectionLostWidget.HTML,
                                                                           heartBeatIntervalMs);
            final PageRenderContext domTreeContext = new PageRenderContext(pageConfigScript.toString(),
                                                                           VirtualDomPath.DOCUMENT,
                                                                           baseUrlPath,
                                                                           httpStateOriginLookup,
                                                                           new TemporaryBufferedPageCommands());

            rootComponentDefinition.render(domTreeContext);

            final RenderedPage<S> pageSnapshot = new RenderedPage<>(httpStateOriginLookup,
                                                                    domTreeContext.rootComponent(),
                                                                    remoteOutReference);
            renderedPages.put(pageId, pageSnapshot);
            final String responseBody = domTreeContext.toString();

            logger.log(TRACE, () -> "Page body: " + responseBody);

            return CompletableFuture.completedFuture(new HttpResponse(domTreeContext.statusCode(),
                                                     headers(domTreeContext.headers(), deviceId),
                                                     responseBody));

        } catch (final Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    private CompletableFuture<HttpResponse> defaultPage404() {
        return CompletableFuture.completedFuture(new HttpResponse(404, List.of(),"Not found"));
    }

    private List<Tuple2<String,String>> headers(final Map<String, String> headers, final String deviceId) {
        final List<Tuple2<String,String>> resultHeaders = new ArrayList<>();
        for (final Map.Entry<String, String> entry : headers.entrySet() ) {
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
}
