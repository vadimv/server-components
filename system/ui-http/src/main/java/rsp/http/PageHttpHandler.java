package rsp.http;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentAccessDeniedException;
import rsp.component.ComponentContext;
import rsp.component.ContextKey;
import rsp.component.definitions.Component;
import rsp.metrics.Metrics;
import rsp.page.DefaultConnectionLostWidget;
import rsp.page.PageBuilder;
import rsp.page.PageConfigScript;
import rsp.page.PageNotFoundException;
import rsp.page.QualifiedSessionId;
import rsp.page.RedirectableEventsConsumer;
import rsp.page.RenderedPage;
import rsp.util.RandomString;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.System.Logger.Level.TRACE;

public final class PageHttpHandler implements HttpApplication {
    private static final System.Logger logger = System.getLogger(PageHttpHandler.class.getName());

    public static final int KEY_LENGTH = 64;
    public static final String DEVICE_ID_COOKIE_NAME = "deviceId";
    public static final String JS_CLIENT_BUNDLE_PATH = "/static/js-client.min.js";

    private final RandomString randomStringGenerator = new RandomString(KEY_LENGTH);

    private final Map<QualifiedSessionId, RenderedPage> renderedPages;
    private final PageApplication pageApplication;
    private final int heartBeatIntervalMs;
    private final Metrics metrics;

    public PageHttpHandler(final Map<QualifiedSessionId, RenderedPage> pagesStorage,
                           final PageApplication pageApplication,
                           final int heartBeatIntervalMs) {

        this(pagesStorage,
             pageApplication,
             heartBeatIntervalMs,
             Metrics.noop());
    }

    public PageHttpHandler(final Map<QualifiedSessionId, RenderedPage> pagesStorage,
                           final PageApplication pageApplication,
                           final int heartBeatIntervalMs,
                           final Metrics metrics) {

        this.renderedPages = Objects.requireNonNull(pagesStorage);
        this.pageApplication = Objects.requireNonNull(pageApplication);
        this.heartBeatIntervalMs = heartBeatIntervalMs;
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public CompletableFuture<HttpResponse> handle(final HttpRequest request) {
        return handlePage(Objects.requireNonNull(request, "request"));
    }

    private CompletableFuture<HttpResponse> handlePage(final HttpRequest request) {
        Objects.requireNonNull(request);
        try {
            PageResult result = pageApplication.handle(request);
            if (result instanceof PageResult.Redirect redirect) {
                HttpResponse.Builder response = HttpResponse.status(redirect.status())
                        .header("Location", redirect.location().toASCIIString());
                redirect.headers().forEach(header -> response.header(header.name(), header.value()));
                return CompletableFuture.completedFuture(response.build());
            }
            if (result instanceof PageResult.Response response) {
                return CompletableFuture.completedFuture(response.response());
            }
            PageResult.Render render = (PageResult.Render) result;
            final String deviceId = request.cookies(DEVICE_ID_COOKIE_NAME).stream().findFirst()
                    .orElse(randomStringGenerator.newString());
            final String sessionId = randomStringGenerator.newString();
            final QualifiedSessionId pageId = new QualifiedSessionId(deviceId, sessionId);

            final PageConfigScript pageConfigScript = new PageConfigScript(sessionId,
                                                                          "/",
                                                                           DefaultConnectionLostWidget.HTML,
                                                                           heartBeatIntervalMs);

            final RedirectableEventsConsumer commandsEnqueue = new RedirectableEventsConsumer();

            final ComponentContext componentContext = new ComponentContext()
                .with(new ContextKey.ClassKey<>(QualifiedSessionId.class), pageId)
                .with(new ContextKey.ClassKey<>(CommandsEnqueue.class), commandsEnqueue)
                .with(Metrics.class, metrics);


            Optional<PageBuilder.LiveBootstrap> bootstrap = render.live()
                    ? Optional.of(new PageBuilder.LiveBootstrap(pageConfigScript.toString(), JS_CLIENT_BUNDLE_PATH))
                    : Optional.empty();
            final PageBuilder pageBuilder = new PageBuilder(pageId,
                                                            bootstrap,
                                                            componentContext,
                                                            commandsEnqueue);

            final Component<?, ?> pageRootComponent = render.component();
            pageRootComponent.render(pageBuilder);
            final List<Throwable> renderExceptions = pageBuilder.exceptions();
            if (renderExceptions.size() > 0) {
                Throwable firstException = renderExceptions.get(0);
                // Map known exceptions to HTTP status codes
                if (firstException instanceof PageNotFoundException) {
                    return CompletableFuture.completedFuture(HttpResponses.status(404));
                } else if (firstException instanceof ComponentAccessDeniedException) {
                    return CompletableFuture.completedFuture(HttpResponses.status(403));
                }
                throw new RuntimeException(firstException);
            }

            if (render.live()) {
                final RenderedPage pageSnapshot = new RenderedPage(pageBuilder, commandsEnqueue);
                renderedPages.put(pageId, pageSnapshot);
            }
            final String responseBody = pageBuilder.html();

            logger.log(TRACE, () -> "Page rendered [status=" + render.status().code()
                    + ", bodyChars=" + responseBody.length() + "]");

            HttpResponse.Builder response = HttpResponse.status(render.status())
                    .text(responseBody, MediaType.HTML_UTF_8)
                    .header("Cache-Control", "no-store, no-cache, must-revalidate");
            render.headers().forEach(header -> response.header(header.name(), header.value()));
            render.cookies().forEach(response::cookie);
            if (render.live()) {
                response.cookie(SetCookie.of(DEVICE_ID_COOKIE_NAME, deviceId)
                        .path("/")
                        .maxAge(Duration.ofDays(3650))
                        .sameSite(SetCookie.SameSite.LAX));
            }
            return CompletableFuture.completedFuture(response.build());

        } catch (final Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

}
