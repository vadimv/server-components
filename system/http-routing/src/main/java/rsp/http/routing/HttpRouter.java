package rsp.http.routing;

import rsp.http.HttpApplication;
import rsp.http.HttpMethod;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;
import rsp.url.Path;
import rsp.url.routing.RouteMatch;
import rsp.url.routing.RouteTable;
import rsp.url.routing.RouteTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Immutable, method-aware HTTP router backed by the generic URL route table. */
public final class HttpRouter implements HttpApplication {
    private final Map<HttpMethod, RouteTable<RegisteredRoute>> routes;
    private final Map<HttpMethod, List<PrefixRegistration>> prefixes;
    private final HttpApplication fallback;

    private HttpRouter(Map<HttpMethod, RouteTable<RegisteredRoute>> routes,
                       Map<HttpMethod, List<PrefixRegistration>> prefixes,
                       HttpApplication fallback) {
        this.routes = Map.copyOf(routes);
        EnumMap<HttpMethod, List<PrefixRegistration>> copiedPrefixes = new EnumMap<>(HttpMethod.class);
        prefixes.forEach((method, registrations) -> copiedPrefixes.put(method, List.copyOf(registrations)));
        this.prefixes = Map.copyOf(copiedPrefixes);
        this.fallback = fallback;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Exact routes exposed without handlers for documentation and tooling. */
    public List<HttpRouteDefinition> routeDefinitions() {
        List<HttpRouteDefinition> result = new ArrayList<>();
        routes.forEach((method, table) -> table.routes().forEach(route -> result.add(
                new HttpRouteDefinition(method, route.template(), route.target().metadata()))));
        return result.stream()
                .sorted(Comparator.comparing((HttpRouteDefinition route) -> route.template().toString())
                        .thenComparing(route -> route.method().ordinal()))
                .toList();
    }

    /** Returns a copy that delegates unknown paths to {@code application}. */
    public HttpRouter withFallback(HttpApplication application) {
        Objects.requireNonNull(application, "application");
        if (fallback != null) {
            throw new IllegalStateException("Router already has a fallback application");
        }
        return new HttpRouter(routes, prefixes, application);
    }

    @Override
    public CompletionStage<HttpResponse> handle(HttpRequest request) {
        Objects.requireNonNull(request, "request");
        Optional<SelectedHandler> match = match(request.method(), request);
        if (match.isEmpty() && request.method() == HttpMethod.HEAD) {
            match = match(HttpMethod.GET, request);
        }
        if (match.isPresent()) {
            return invoke(match.get(), request);
        }

        Set<String> allowed = allowedMethods(request);
        if (!allowed.isEmpty()) {
            return CompletableFuture.completedFuture(HttpResponse.status(HttpStatus.METHOD_NOT_ALLOWED)
                    .header("Allow", String.join(", ", allowed))
                    .build());
        }

        if (fallback != null) {
            try {
                return Objects.requireNonNull(fallback.handle(request), "fallback completion stage");
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }
        return CompletableFuture.completedFuture(HttpResponse.status(HttpStatus.NOT_FOUND).build());
    }

    @Override
    public void start() {
        if (fallback != null) {
            fallback.start();
        }
    }

    @Override
    public void stop() {
        if (fallback != null) {
            fallback.stop();
        }
    }

    private Optional<SelectedHandler> match(HttpMethod method, HttpRequest request) {
        RouteTable<RegisteredRoute> table = routes.get(method);
        Optional<RouteMatch<RegisteredRoute>> exact = table == null
                ? Optional.empty()
                : table.match(request.path());
        if (exact.isPresent()) {
            RouteMatch<RegisteredRoute> selected = exact.get();
            return Optional.of(selectedRequest -> selected.target().handler().handle(
                    selectedRequest, new HttpRouteContext(selected.template(), selected.path())));
        }

        return matchingPrefix(method, request.path()).map(selected -> selectedRequest ->
                selected.handler().handle(selectedRequest,
                        new HttpPrefixContext(selected.prefix(), request.path().relativize(selected.prefix()))));
    }

    private Set<String> allowedMethods(HttpRequest request) {
        Set<String> result = new TreeSet<>();
        for (HttpMethod method : HttpMethod.values()) {
            RouteTable<RegisteredRoute> table = routes.get(method);
            if ((table != null && table.match(request.path()).isPresent())
                    || matchingPrefix(method, request.path()).isPresent()) {
                result.add(method.name());
                if (method == HttpMethod.GET) {
                    result.add(HttpMethod.HEAD.name());
                }
            }
        }
        return result;
    }

    private Optional<PrefixRegistration> matchingPrefix(HttpMethod method, Path path) {
        return prefixes.getOrDefault(method, List.of()).stream()
                .filter(prefix -> path.startsWith(prefix.prefix()))
                .findFirst();
    }

    private static CompletionStage<HttpResponse> invoke(SelectedHandler selected, HttpRequest request) {
        try {
            return Objects.requireNonNull(selected.handle(request), "handler completion stage");
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /** Mutable assembly DSL producing an immutable router. */
    public static final class Builder {
        private final Map<HttpMethod, List<Registration>> registrations = new EnumMap<>(HttpMethod.class);
        private final Map<HttpMethod, List<PrefixRegistration>> prefixes = new EnumMap<>(HttpMethod.class);
        private HttpApplication fallback;

        public Builder route(HttpMethod method, String template, HttpRouteHandler handler) {
            return route(method, template, handler, new HttpRouteMetadata[0]);
        }

        public Builder route(HttpMethod method,
                             String template,
                             HttpRouteHandler handler,
                             HttpRouteMetadata... metadata) {
            return route(method, RouteTemplate.parse(template), handler, metadata);
        }

        public Builder route(HttpMethod method, RouteTemplate template, HttpRouteHandler handler) {
            return route(method, template, handler, new HttpRouteMetadata[0]);
        }

        public Builder route(HttpMethod method,
                             RouteTemplate template,
                             HttpRouteHandler handler,
                             HttpRouteMetadata... metadata) {
            registrations.computeIfAbsent(Objects.requireNonNull(method, "method"), _ -> new ArrayList<>())
                    .add(new Registration(Objects.requireNonNull(template, "template"),
                            Objects.requireNonNull(handler, "handler"), List.of(metadata)));
            return this;
        }

        public Builder get(String template, HttpRouteHandler handler) {
            return route(HttpMethod.GET, template, handler);
        }

        public Builder get(String template, HttpRouteHandler handler, HttpRouteMetadata... metadata) {
            return route(HttpMethod.GET, template, handler, metadata);
        }

        public Builder head(String template, HttpRouteHandler handler) {
            return route(HttpMethod.HEAD, template, handler);
        }

        public Builder head(String template, HttpRouteHandler handler, HttpRouteMetadata... metadata) {
            return route(HttpMethod.HEAD, template, handler, metadata);
        }

        public Builder post(String template, HttpRouteHandler handler) {
            return route(HttpMethod.POST, template, handler);
        }

        public Builder post(String template, HttpRouteHandler handler, HttpRouteMetadata... metadata) {
            return route(HttpMethod.POST, template, handler, metadata);
        }

        public Builder put(String template, HttpRouteHandler handler) {
            return route(HttpMethod.PUT, template, handler);
        }

        public Builder put(String template, HttpRouteHandler handler, HttpRouteMetadata... metadata) {
            return route(HttpMethod.PUT, template, handler, metadata);
        }

        public Builder patch(String template, HttpRouteHandler handler) {
            return route(HttpMethod.PATCH, template, handler);
        }

        public Builder patch(String template, HttpRouteHandler handler, HttpRouteMetadata... metadata) {
            return route(HttpMethod.PATCH, template, handler, metadata);
        }

        public Builder delete(String template, HttpRouteHandler handler) {
            return route(HttpMethod.DELETE, template, handler);
        }

        public Builder delete(String template, HttpRouteHandler handler, HttpRouteMetadata... metadata) {
            return route(HttpMethod.DELETE, template, handler, metadata);
        }

        public Builder options(String template, HttpRouteHandler handler) {
            return route(HttpMethod.OPTIONS, template, handler);
        }

        public Builder options(String template, HttpRouteHandler handler, HttpRouteMetadata... metadata) {
            return route(HttpMethod.OPTIONS, template, handler, metadata);
        }

        /** Registers a literal path prefix. Exact template routes take precedence. */
        public Builder prefix(HttpMethod method, String pathPrefix, HttpPrefixHandler handler) {
            Objects.requireNonNull(pathPrefix, "pathPrefix");
            if (!pathPrefix.startsWith("/")) {
                throw new IllegalArgumentException("A route prefix must be absolute: " + pathPrefix);
            }
            Path prefix = Path.parse(pathPrefix);
            prefixes.computeIfAbsent(Objects.requireNonNull(method, "method"), _ -> new ArrayList<>())
                    .add(new PrefixRegistration(prefix, Objects.requireNonNull(handler, "handler")));
            return this;
        }

        public Builder getPrefix(String pathPrefix, HttpPrefixHandler handler) {
            return prefix(HttpMethod.GET, pathPrefix, handler);
        }

        public Builder headPrefix(String pathPrefix, HttpPrefixHandler handler) {
            return prefix(HttpMethod.HEAD, pathPrefix, handler);
        }

        public Builder postPrefix(String pathPrefix, HttpPrefixHandler handler) {
            return prefix(HttpMethod.POST, pathPrefix, handler);
        }

        public Builder putPrefix(String pathPrefix, HttpPrefixHandler handler) {
            return prefix(HttpMethod.PUT, pathPrefix, handler);
        }

        public Builder patchPrefix(String pathPrefix, HttpPrefixHandler handler) {
            return prefix(HttpMethod.PATCH, pathPrefix, handler);
        }

        public Builder deletePrefix(String pathPrefix, HttpPrefixHandler handler) {
            return prefix(HttpMethod.DELETE, pathPrefix, handler);
        }

        public Builder optionsPrefix(String pathPrefix, HttpPrefixHandler handler) {
            return prefix(HttpMethod.OPTIONS, pathPrefix, handler);
        }

        /** Adds every route from a non-terminal router. */
        public Builder include(HttpRouter router) {
            Objects.requireNonNull(router, "router");
            if (router.fallback != null) {
                throw new IllegalArgumentException("Cannot include a router that has a fallback application");
            }
            router.routes.forEach((method, table) -> table.routes().forEach(route ->
                    route(method, route.template(), route.target().handler(),
                            route.target().metadata().toArray(HttpRouteMetadata[]::new))));
            router.prefixes.forEach((method, methodPrefixes) -> methodPrefixes.forEach(prefix ->
                    prefixes.computeIfAbsent(method, _ -> new ArrayList<>()).add(prefix)));
            return this;
        }

        /** Handles paths that are unknown to every method-specific route. */
        public Builder fallback(HttpApplication application) {
            if (fallback != null) {
                throw new IllegalStateException("Router already has a fallback application");
            }
            fallback = Objects.requireNonNull(application, "application");
            return this;
        }

        public HttpRouter build() {
            Map<HttpMethod, RouteTable<RegisteredRoute>> built = new EnumMap<>(HttpMethod.class);
            registrations.forEach((method, methodRoutes) -> {
                RouteTable.Builder<RegisteredRoute> table = RouteTable.builder();
                methodRoutes.forEach(route -> table.route(route.template(),
                        new RegisteredRoute(route.handler(), route.metadata())));
                built.put(method, table.build());
            });

            EnumMap<HttpMethod, List<PrefixRegistration>> builtPrefixes = new EnumMap<>(HttpMethod.class);
            prefixes.forEach((method, methodPrefixes) -> {
                Set<Path> seen = new HashSet<>();
                methodPrefixes.forEach(prefix -> {
                    if (!seen.add(prefix.prefix())) {
                        throw new IllegalArgumentException("Duplicate " + method
                                + " route prefix: " + prefix.prefix());
                    }
                });
                builtPrefixes.put(method, methodPrefixes.stream()
                        .sorted(Comparator.comparingInt((PrefixRegistration prefix) ->
                                        prefix.prefix().elementsCount())
                                .reversed()
                                .thenComparing(prefix -> prefix.prefix().toString()))
                        .toList());
            });
            return new HttpRouter(built, builtPrefixes, fallback);
        }
    }

    private record Registration(RouteTemplate template,
                                HttpRouteHandler handler,
                                List<HttpRouteMetadata> metadata) {
        private Registration {
            metadata = List.copyOf(Objects.requireNonNull(metadata, "metadata"));
        }
    }

    private record RegisteredRoute(HttpRouteHandler handler, List<HttpRouteMetadata> metadata) {
        private RegisteredRoute {
            Objects.requireNonNull(handler, "handler");
            metadata = List.copyOf(Objects.requireNonNull(metadata, "metadata"));
        }
    }

    private record PrefixRegistration(Path prefix, HttpPrefixHandler handler) {
        private PrefixRegistration {
            Objects.requireNonNull(prefix, "prefix");
            Objects.requireNonNull(handler, "handler");
        }
    }

    @FunctionalInterface
    private interface SelectedHandler {
        CompletionStage<HttpResponse> handle(HttpRequest request);
    }
}
