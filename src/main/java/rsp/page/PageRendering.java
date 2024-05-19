package rsp.page;

import rsp.component.StatefulComponentDefinition;
import rsp.dom.TreePositionPath;
import rsp.server.http.*;
import rsp.server.Path;
import rsp.util.RandomString;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.System.Logger.Level.TRACE;

public final class PageRendering<S> {
    private static final System.Logger logger = System.getLogger(PageRendering.class.getName());

    public static final TreePositionPath DOCUMENT_DOM_PATH = TreePositionPath.of("1");
    public static final TreePositionPath WINDOW_DOM_PATH = TreePositionPath.of("");

    public static final int KEY_LENGTH = 64;
    public static final String DEVICE_ID_COOKIE_NAME = "deviceId";

    private final RandomString randomStringGenerator = new RandomString(KEY_LENGTH);

    private final Map<QualifiedSessionId, RenderedPage> renderedPages;
    private final StatefulComponentDefinition<S> rootComponentDefinition;
    private final int heartBeatIntervalMs;

    public PageRendering(final Map<QualifiedSessionId, RenderedPage> pagesStorage,
                         final StatefulComponentDefinition<S> rootComponentDefinition,
                         final int heartBeatIntervalMs) {

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
        if (!path.contains("..")) {
            final URL fileUrl =  this.getClass().getResource(path.toString());
            if (fileUrl != null) {
                try {
                    return CompletableFuture.completedFuture(new HttpResponse(200,
                                                                               Collections.emptyList(),
                                                                               fileUrl.openStream()));
                } catch (final IOException e) {
                    return CompletableFuture.completedFuture(new HttpResponse(500,
                                                                                Collections.emptyList(),
                                                                                "Exception on loading a static resource: "
                                                                                        + path
                                                                                        + " " + e.getMessage()));
                }
            }
        }
        return CompletableFuture.completedFuture(new HttpResponse(404,
                                                                    Collections.emptyList(),
                                                                    "Resource not found: " + path));

    }

    private CompletableFuture<HttpResponse> rspResponse(final HttpRequest request) {
        try {
            final String deviceId = request.cookie(DEVICE_ID_COOKIE_NAME).orElse(randomStringGenerator.newString());
            final String sessionId = randomStringGenerator.newString();
            final QualifiedSessionId pageId = new QualifiedSessionId(deviceId, sessionId);

            final PageStateOrigin httpStateOrigin = new PageStateOrigin(request);
            final PageConfigScript pageConfigScript = new PageConfigScript(sessionId,
                                                                          "/",
                                                                           DefaultConnectionLostWidget.HTML,
                                                                           heartBeatIntervalMs);

            final TemporaryBufferedPageCommands commandsBuffer = new TemporaryBufferedPageCommands();
            final Object sessionLock = new Object();
            final PageRenderContext pageRenderContext = new PageRenderContext(pageId,
                                                                              pageConfigScript.toString(),
                    DOCUMENT_DOM_PATH,
                                                                              httpStateOrigin,
                                                                              commandsBuffer,
                                                                              sessionLock);

            rootComponentDefinition.render(pageRenderContext);

            final RenderedPage pageSnapshot = new RenderedPage(pageRenderContext,
                                                               commandsBuffer,
                                                               sessionLock);
            renderedPages.put(pageId, pageSnapshot);
            final String responseBody = pageRenderContext.html();

            logger.log(TRACE, () -> "Page body: " + responseBody);

            return CompletableFuture.completedFuture(new HttpResponse(pageRenderContext.statusCode(),
                                                                      headers(pageRenderContext.headers(), deviceId),
                                                                      responseBody));

        } catch (final Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    private List<Header> headers(final Map<String, String> headers, final String deviceId) {
        final List<Header> resultHeaders = new ArrayList<>();
        for (final Map.Entry<String, String> entry : headers.entrySet() ) {
            resultHeaders.add(new Header(entry.getKey(), entry.getValue()));
        }
        resultHeaders.add(new Header("content-type", "text/html; charset=utf-8"));
        resultHeaders.add(new Header("cache-control", "no-store, no-cache, must-revalidate"));
        resultHeaders.add(new Header("Set-Cookie", String.format("%s=%s; Path=%s; Max-Age=%d; SameSite=Lax",
                                                                       DEVICE_ID_COOKIE_NAME,
                                                                       deviceId,
                                                                       "/",
                                                                       60 * 60 * 24 * 365 * 10 /* 10 years */ )));
                return resultHeaders;

    }
}
