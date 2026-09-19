package rsp.http.middleware;

import rsp.http.HttpApplication;
import rsp.http.HttpHeader;
import rsp.http.HttpHeaders;
import rsp.http.HttpMiddleware;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Overwrites selected response headers at the application boundary. */
public final class SecurityHeadersMiddleware implements HttpMiddleware {
    private final HttpHeaders headers;

    public SecurityHeadersMiddleware(HttpHeaders headers) {
        this.headers = HttpHeaders.copyOf(Objects.requireNonNull(headers, "headers"));
    }

    /**
     * A conservative browser baseline without CSP or HSTS deployment assumptions.
     */
    public static SecurityHeadersMiddleware defaults() {
        return new SecurityHeadersMiddleware(HttpHeaders.of(
                new HttpHeader("X-Content-Type-Options", "nosniff"),
                new HttpHeader("X-Frame-Options", "DENY"),
                new HttpHeader("Referrer-Policy", "strict-origin-when-cross-origin")));
    }

    @Override
    public CompletionStage<HttpResponse> handle(HttpRequest request, HttpApplication next) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(next, "next");
        return next.handle(request).thenApply(response -> {
            HttpResponse result = Objects.requireNonNull(response, "application response");
            for (HttpHeader header : headers) {
                result = result.withHeader(header.name(), header.value());
            }
            return result;
        });
    }
}
