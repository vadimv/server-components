package rsp.http.middleware;

import rsp.http.HttpHeader;
import rsp.http.HttpMethod;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable allow-list used by {@link CorsMiddleware}. */
public final class CorsPolicy {
    private final boolean anyOrigin;
    private final Set<String> origins;
    private final Set<HttpMethod> methods;
    private final List<String> allowedHeaders;
    private final Set<String> normalizedAllowedHeaders;
    private final List<String> exposedHeaders;
    private final boolean credentials;
    private final Optional<Duration> maxAge;

    private CorsPolicy(Builder builder) {
        if (!builder.anyOrigin && builder.origins.isEmpty()) {
            throw new IllegalStateException("At least one allowed CORS origin is required");
        }
        if (builder.methods.isEmpty()) {
            throw new IllegalStateException("At least one allowed CORS method is required");
        }
        if (builder.anyOrigin && builder.credentials) {
            throw new IllegalStateException("Credentialed CORS cannot allow every origin");
        }
        anyOrigin = builder.anyOrigin;
        origins = Collections.unmodifiableSet(new LinkedHashSet<>(builder.origins));
        methods = Collections.unmodifiableSet(EnumSet.copyOf(builder.methods));
        allowedHeaders = List.copyOf(builder.allowedHeaders);
        normalizedAllowedHeaders = allowedHeaders.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        exposedHeaders = List.copyOf(builder.exposedHeaders);
        credentials = builder.credentials;
        maxAge = Optional.ofNullable(builder.maxAge);
    }

    public static Builder builder() {
        return new Builder();
    }

    boolean allowsOrigin(String origin) {
        return anyOrigin || origins.contains(origin);
    }

    boolean allowsMethod(HttpMethod method) {
        return methods.contains(method);
    }

    boolean allowsHeader(String header) {
        return normalizedAllowedHeaders.contains(header.toLowerCase(Locale.ROOT));
    }

    String responseOrigin(String origin) {
        return anyOrigin ? "*" : origin;
    }

    boolean variesByOrigin() {
        return !anyOrigin;
    }

    Set<HttpMethod> methods() {
        return methods;
    }

    List<String> exposedHeaders() {
        return exposedHeaders;
    }

    boolean credentials() {
        return credentials;
    }

    Optional<Duration> maxAge() {
        return maxAge;
    }

    public static final class Builder {
        private boolean anyOrigin;
        private final Set<String> origins = new LinkedHashSet<>();
        private final Set<HttpMethod> methods = EnumSet.noneOf(HttpMethod.class);
        private final List<String> allowedHeaders = new ArrayList<>();
        private final List<String> exposedHeaders = new ArrayList<>();
        private boolean credentials;
        private Duration maxAge;

        public Builder allowAnyOrigin() {
            anyOrigin = true;
            origins.clear();
            return this;
        }

        public Builder allowOrigin(String origin) {
            String value = Objects.requireNonNull(origin, "origin");
            if (value.isBlank() || value.equals("*") || containsControl(value)) {
                throw new IllegalArgumentException("Invalid CORS origin: " + value);
            }
            anyOrigin = false;
            origins.add(value);
            return this;
        }

        public Builder allowMethods(HttpMethod... values) {
            Objects.requireNonNull(values, "values");
            for (HttpMethod value : values) {
                methods.add(Objects.requireNonNull(value, "CORS method"));
            }
            return this;
        }

        public Builder allowHeaders(String... values) {
            addHeaderNames(allowedHeaders, values);
            return this;
        }

        public Builder exposeHeaders(String... values) {
            addHeaderNames(exposedHeaders, values);
            return this;
        }

        public Builder allowCredentials() {
            credentials = true;
            return this;
        }

        public Builder maxAge(Duration value) {
            Objects.requireNonNull(value, "value");
            if (value.isNegative()) {
                throw new IllegalArgumentException("CORS max age must not be negative");
            }
            maxAge = value;
            return this;
        }

        public CorsPolicy build() {
            return new CorsPolicy(this);
        }

        private static void addHeaderNames(List<String> target, String[] values) {
            Objects.requireNonNull(values, "values");
            for (String value : values) {
                String name = Objects.requireNonNull(value, "header name");
                new HttpHeader(name, "");
                if (target.stream().noneMatch(existing -> existing.equalsIgnoreCase(name))) {
                    target.add(name);
                }
            }
        }

        private static boolean containsControl(String value) {
            return value.chars().anyMatch(character -> character <= 0x1f || character == 0x7f);
        }
    }
}
