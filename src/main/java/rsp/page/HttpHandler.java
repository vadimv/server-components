package rsp.page;

import rsp.component.ComponentContext;
import rsp.component.definitions.Component;
import rsp.server.StaticResourceHandler;
import rsp.server.http.*;
import rsp.server.Path;
import rsp.util.RandomString;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.lang.System.Logger.Level.TRACE;

public final class HttpHandler {
    private static final System.Logger logger = System.getLogger(HttpHandler.class.getName());

    public static final int KEY_LENGTH = 64;
    public static final String DEVICE_ID_COOKIE_NAME = "deviceId";
    public static final String JS_CLIENT_BUNDLE_PATH = "/static/rsp-client.min.js";

    private final RandomString randomStringGenerator = new RandomString(KEY_LENGTH);

    private final Map<QualifiedSessionId, RenderedPage> renderedPages;
    private final Function<HttpRequest, Component<?>> rootComponentDefinition;
    private final Optional<StaticResourceHandler> staticResourceHandler;
    private final int heartBeatIntervalMs;

    public HttpHandler(final Map<QualifiedSessionId, RenderedPage> pagesStorage,
                       final Function<HttpRequest, Component<?>> rootComponentDefinition,
                       final Optional<StaticResourceHandler> staticResourceHandler,
                       final int heartBeatIntervalMs) {

        this.renderedPages = Objects.requireNonNull(pagesStorage);
        this.rootComponentDefinition = Objects.requireNonNull(rootComponentDefinition);
        this.staticResourceHandler = Objects.requireNonNull(staticResourceHandler);
        this.heartBeatIntervalMs = heartBeatIntervalMs;
    }

    public CompletableFuture<HttpResponse> handle(final HttpRequest request) {
        Objects.requireNonNull(request);
        if (request.path.endsWith("favicon.ico")) {
            return CompletableFuture.completedFuture(new HttpResponse(404, Collections.emptyList(), "No favicon.ico"));
        } else if (request.path.toString().equals(JS_CLIENT_BUNDLE_PATH)) {
            return CompletableFuture.completedFuture(jsClientBundleResponse());
        } else if (staticResourceHandler.isPresent() && staticResourceHandler.get().shouldHandle(request.path)) {
            return CompletableFuture.completedFuture(staticResourceHandler.get().handle(request.path));
        } else {
            return handlePage(request);
        }
    }

    private HttpResponse jsClientBundleResponse() {
        final InputStream inputStream = this.getClass().getResourceAsStream(JS_CLIENT_BUNDLE_PATH);
        if (inputStream != null) {
            return new HttpResponse(200,
                                    Collections.singletonList(new Header("Content-Type", "application/javascript")),
                                    inputStream);
        } else {
            return new HttpResponse(500,
                                    Collections.emptyList(),
                                    "rsp-client.min.js not found in classpath");
        }
    }

    private HttpResponse staticFileResponse(final Path path) {
        Objects.requireNonNull(path);
        if (!path.contains("..")) {
            final URL fileUrl =  this.getClass().getResource(path.toString());
            if (fileUrl != null) {
                try {
                    return new HttpResponse(200,
                                            Collections.emptyList(),
                                            fileUrl.openStream());
                } catch (final IOException e) {
                    return new HttpResponse(500,
                                            Collections.emptyList(),
                                            "Exception on loading a static resource: "
                                                    + path
                                                    + " " + e.getMessage());
                }
            }
        }
        return new HttpResponse(404,
                                Collections.emptyList(),
                                "Resource not found: " + path);

    }

    private CompletableFuture<HttpResponse> handlePage(final HttpRequest request) {
        Objects.requireNonNull(request);
        try {
            final String deviceId = Optional.ofNullable(request.deviceId()).orElse(randomStringGenerator.newString());
            final String sessionId = randomStringGenerator.newString();
            final QualifiedSessionId pageId = new QualifiedSessionId(deviceId, sessionId);

            final PageConfigScript pageConfigScript = new PageConfigScript(sessionId,
                                                                          "/",
                                                                           DefaultConnectionLostWidget.HTML,
                                                                           heartBeatIntervalMs);

            final ComponentContext componentContext = new ComponentContext().with(Map.of("deviceId", deviceId,
                                                                                         "sessionId", sessionId));

            final RedirectableEventsConsumer commandsEnqueue = new RedirectableEventsConsumer();

            final PageBuilder pageBuilder = new PageBuilder(pageId,
                                                            pageConfigScript.toString(),
                                                            componentContext,
                                                            commandsEnqueue);

            rootComponentDefinition.apply(request).render(pageBuilder);

            final RenderedPage pageSnapshot = new RenderedPage(pageBuilder, commandsEnqueue);
            renderedPages.put(pageId, pageSnapshot);
            final String responseBody = pageBuilder.html();

            logger.log(TRACE, () -> "Page body: " + responseBody);

            return CompletableFuture.completedFuture(new HttpResponse(pageBuilder.statusCode(),
                                                                      renderedHeaders(pageBuilder.headers(), deviceId),
                                                                      responseBody));

        } catch (final Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    private List<Header> renderedHeaders(final Map<String, List<String>> headers, final String deviceId) {
        Objects.requireNonNull(headers);
        Objects.requireNonNull(deviceId);
        final List<Header> resultHeaders = new ArrayList<>();
        for (final Map.Entry<String, List<String>> entry : headers.entrySet() ) {
            for (final String value : entry.getValue()) {
                resultHeaders.add(new Header(entry.getKey(), value));
            }
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
