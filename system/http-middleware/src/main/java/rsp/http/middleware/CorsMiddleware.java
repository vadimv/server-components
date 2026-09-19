package rsp.http.middleware;

import rsp.http.HttpApplication;
import rsp.http.HttpHeaders;
import rsp.http.HttpMethod;
import rsp.http.HttpMiddleware;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/** Applies an explicit CORS allow-list and answers valid preflight requests. */
public final class CorsMiddleware implements HttpMiddleware {
    private static final String ORIGIN = "Origin";
    private static final String REQUEST_METHOD = "Access-Control-Request-Method";
    private static final String REQUEST_HEADERS = "Access-Control-Request-Headers";

    private final CorsPolicy policy;

    public CorsMiddleware(CorsPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public CompletionStage<HttpResponse> handle(HttpRequest request, HttpApplication next) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(next, "next");
        List<String> origins = request.headers().all(ORIGIN);
        if (origins.isEmpty()) {
            return next.handle(request);
        }
        boolean preflight = request.method() == HttpMethod.OPTIONS && request.header(REQUEST_METHOD) != null;
        if (origins.size() != 1 || !policy.allowsOrigin(origins.getFirst())) {
            return preflight ? denied() : next.handle(request);
        }
        String origin = origins.getFirst();
        if (preflight) {
            return preflight(request, origin);
        }
        if (!policy.allowsMethod(request.method())) {
            return next.handle(request);
        }
        return next.handle(request).thenApply(response -> corsResponse(
                Objects.requireNonNull(response, "application response"), origin, false, List.of()));
    }

    private CompletionStage<HttpResponse> preflight(HttpRequest request, String origin) {
        HttpMethod requestedMethod;
        try {
            requestedMethod = HttpMethod.valueOf(request.header(REQUEST_METHOD).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            return denied();
        }
        List<String> requestedHeaders = requestedHeaders(request.headers());
        if (!policy.allowsMethod(requestedMethod)
                || requestedHeaders == null
                || requestedHeaders.stream().anyMatch(header -> !policy.allowsHeader(header))) {
            return denied();
        }
        HttpResponse response = HttpResponse.status(HttpStatus.NO_CONTENT).build();
        return CompletableFuture.completedFuture(corsResponse(response, origin, true, requestedHeaders));
    }

    private HttpResponse corsResponse(HttpResponse initial,
                                      String origin,
                                      boolean preflight,
                                      List<String> requestedHeaders) {
        HttpResponse response = initial.withHeader("Access-Control-Allow-Origin", policy.responseOrigin(origin));
        if (policy.credentials()) {
            response = response.withHeader("Access-Control-Allow-Credentials", "true");
        }
        List<String> vary = new ArrayList<>();
        if (policy.variesByOrigin()) {
            vary.add(ORIGIN);
        }
        if (preflight) {
            response = response.withHeader("Access-Control-Allow-Methods", policy.methods().stream()
                    .map(Enum::name).sorted().collect(Collectors.joining(", ")));
            if (!requestedHeaders.isEmpty()) {
                response = response.withHeader("Access-Control-Allow-Headers", String.join(", ", requestedHeaders));
            }
            if (policy.maxAge().isPresent()) {
                response = response.withHeader("Access-Control-Max-Age",
                        Long.toString(policy.maxAge().orElseThrow().toSeconds()));
            }
            vary.add(REQUEST_METHOD);
            vary.add(REQUEST_HEADERS);
        } else if (!policy.exposedHeaders().isEmpty()) {
            response = response.withHeader("Access-Control-Expose-Headers",
                    String.join(", ", policy.exposedHeaders()));
        }
        return appendVary(response, vary);
    }

    private static List<String> requestedHeaders(HttpHeaders headers) {
        List<String> values = headers.all(REQUEST_HEADERS);
        if (values.isEmpty()) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        try {
            for (String value : values) {
                for (String header : value.split(",", -1)) {
                    String name = header.trim();
                    if (name.isEmpty()) {
                        return null;
                    }
                    new rsp.http.HttpHeader(name, "");
                    result.add(name);
                }
            }
        } catch (IllegalArgumentException failure) {
            return null;
        }
        return List.copyOf(result);
    }

    private static HttpResponse appendVary(HttpResponse response, List<String> additional) {
        if (additional.isEmpty()) {
            return response;
        }
        Set<String> values = new LinkedHashSet<>();
        response.headers().all("Vary").forEach(value -> {
            for (String item : value.split(",")) {
                if (!item.isBlank()) {
                    values.add(item.trim());
                }
            }
        });
        for (String value : additional) {
            if (values.stream().noneMatch(existing -> existing.equalsIgnoreCase(value))) {
                values.add(value);
            }
        }
        return response.withHeader("Vary", String.join(", ", values));
    }

    private static CompletionStage<HttpResponse> denied() {
        return CompletableFuture.completedFuture(HttpResponse.status(HttpStatus.FORBIDDEN).build());
    }
}
