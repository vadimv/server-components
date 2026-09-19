package rsp.http.middleware;

import rsp.http.HttpApplication;
import rsp.http.HttpMiddleware;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

/** Converts unexpected application failures to a body-free, sanitized {@code 500} response. */
public final class ServerErrorMiddleware implements HttpMiddleware {
    private final BiConsumer<HttpRequest, Throwable> reporter;

    public ServerErrorMiddleware() {
        this((_, _) -> { });
    }

    public ServerErrorMiddleware(BiConsumer<HttpRequest, Throwable> reporter) {
        this.reporter = Objects.requireNonNull(reporter, "reporter");
    }

    /** Reports only bounded request data and the failure class through the JDK logging facade. */
    public static ServerErrorMiddleware systemLogger(System.Logger logger) {
        Objects.requireNonNull(logger, "logger");
        return new ServerErrorMiddleware((request, failure) ->
                logger.log(System.Logger.Level.ERROR, () ->
                        "HTTP application failed [method=" + request.method()
                                + ", path=" + request.rawPath()
                                + ", failure=" + failure.getClass().getName() + "]"));
    }

    @Override
    public CompletionStage<HttpResponse> handle(HttpRequest request, HttpApplication next) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(next, "next");
        CompletionStage<HttpResponse> stage;
        try {
            stage = Objects.requireNonNull(next.handle(request), "application completion stage");
        } catch (Throwable failure) {
            return recovered(request, failure);
        }
        return stage.handle((response, failure) -> {
            if (failure == null && response != null) {
                return response;
            }
            Throwable actual = failure == null
                    ? new NullPointerException("application response") : unwrap(failure);
            report(request, actual);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        });
    }

    private CompletionStage<HttpResponse> recovered(HttpRequest request, Throwable failure) {
        report(request, unwrap(failure));
        return CompletableFuture.completedFuture(
                HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }

    private void report(HttpRequest request, Throwable failure) {
        try {
            reporter.accept(request, failure);
        } catch (Throwable ignored) {
            // Diagnostics must never replace the sanitized failure response.
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable result = failure;
        while ((result instanceof CompletionException || result instanceof ExecutionException)
                && result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }
}
