package rsp.http.middleware;

import rsp.http.HttpApplication;
import rsp.http.HttpMiddleware;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Adds one bounded correlation identifier to the request and response. */
public final class RequestIdMiddleware implements HttpMiddleware {
    public static final String HEADER_NAME = "X-Request-ID";
    private static final int MAXIMUM_LENGTH = 128;
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._:-]+");

    private final Supplier<String> generator;

    /** Creates middleware that preserves safe incoming identifiers and otherwise uses a UUID. */
    public RequestIdMiddleware() {
        this(() -> UUID.randomUUID().toString());
    }

    /** Creates middleware with an injectable identifier generator. */
    public RequestIdMiddleware(Supplier<String> generator) {
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    @Override
    public CompletionStage<HttpResponse> handle(HttpRequest request, HttpApplication next) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(next, "next");
        String requestId = request.headers().all(HEADER_NAME).size() == 1
                ? valid(request.header(HEADER_NAME))
                : null;
        if (requestId == null) {
            requestId = requireValidGenerated(generator.get());
        }
        final String correlationId = requestId;
        return next.handle(request.withHeader(HEADER_NAME, correlationId))
                .thenApply(response -> Objects.requireNonNull(response, "application response")
                        .withHeader(HEADER_NAME, correlationId));
    }

    private static String valid(String value) {
        return value != null && value.length() <= MAXIMUM_LENGTH && SAFE_VALUE.matcher(value).matches()
                ? value : null;
    }

    private static String requireValidGenerated(String value) {
        String valid = valid(value);
        if (valid == null) {
            throw new IllegalStateException("Request ID generator returned an invalid identifier");
        }
        return valid;
    }
}
