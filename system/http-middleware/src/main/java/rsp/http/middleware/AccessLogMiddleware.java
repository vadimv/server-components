package rsp.http.middleware;

import rsp.http.HttpApplication;
import rsp.http.HttpMiddleware;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.LongSupplier;

/** Emits one access event after a response or exceptional completion. */
public final class AccessLogMiddleware implements HttpMiddleware {
    private final HttpAccessLogger accessLogger;
    private final Clock clock;
    private final LongSupplier nanoTime;

    public AccessLogMiddleware(HttpAccessLogger accessLogger) {
        this(accessLogger, Clock.systemUTC(), System::nanoTime);
    }

    AccessLogMiddleware(HttpAccessLogger accessLogger, Clock clock, LongSupplier nanoTime) {
        this.accessLogger = Objects.requireNonNull(accessLogger, "accessLogger");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    /** Creates an access logger backed by the JDK logging facade. */
    public static AccessLogMiddleware systemLogger(System.Logger logger) {
        return new AccessLogMiddleware(HttpAccessLogger.system(logger));
    }

    @Override
    public CompletionStage<HttpResponse> handle(HttpRequest request, HttpApplication next) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(next, "next");
        Instant startedAt = clock.instant();
        long startedNanos = nanoTime.getAsLong();
        CompletionStage<HttpResponse> stage;
        try {
            stage = Objects.requireNonNull(next.handle(request), "application completion stage");
        } catch (Throwable failure) {
            emit(request, startedAt, startedNanos, null, failure);
            return CompletableFuture.failedFuture(failure);
        }

        CompletableFuture<HttpResponse> result = new CompletableFuture<>();
        stage.whenComplete((response, failure) -> {
            Throwable actualFailure = failure;
            if (actualFailure == null && response == null) {
                actualFailure = new NullPointerException("application response");
            }
            emit(request, startedAt, startedNanos, response, actualFailure);
            if (actualFailure == null) {
                result.complete(response);
            } else {
                result.completeExceptionally(actualFailure);
            }
        });
        return result;
    }

    private void emit(HttpRequest request,
                      Instant startedAt,
                      long startedNanos,
                      HttpResponse response,
                      Throwable failure) {
        long elapsed = Math.max(0, nanoTime.getAsLong() - startedNanos);
        Throwable cause = unwrap(failure);
        HttpAccessEvent event = new HttpAccessEvent(
                startedAt,
                request.method(),
                request.rawPath(),
                Optional.ofNullable(request.header(RequestIdMiddleware.HEADER_NAME)),
                response == null ? 500 : response.status().code(),
                Duration.ofNanos(elapsed),
                cause == null ? Optional.empty() : Optional.of(cause.getClass().getName()));
        try {
            accessLogger.log(event);
        } catch (Throwable ignored) {
            // Diagnostics must never change the application result.
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
