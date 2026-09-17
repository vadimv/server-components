package rsp.http.routing;

import rsp.http.HttpApplication;
import rsp.http.HttpMethod;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;
import rsp.url.routing.RouteMatch;
import rsp.url.routing.RouteTable;
import rsp.url.routing.RouteTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
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
    private final Map<HttpMethod, RouteTable<HttpRouteHandler>> routes;

    private HttpRouter(Map<HttpMethod, RouteTable<HttpRouteHandler>> routes) {
        this.routes = Map.copyOf(routes);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public CompletionStage<HttpResponse> handle(HttpRequest request) {
        Objects.requireNonNull(request, "request");
        Optional<RouteMatch<HttpRouteHandler>> match = match(request.method(), request);
        if (match.isEmpty() && request.method() == HttpMethod.HEAD) {
            match = match(HttpMethod.GET, request);
        }
        if (match.isPresent()) {
            RouteMatch<HttpRouteHandler> selected = match.get();
            try {
                return Objects.requireNonNull(selected.target().handle(
                        request, new HttpRouteContext(selected.template(), selected.path())),
                        "handler completion stage");
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }

        Set<String> allowed = allowedMethods(request);
        if (allowed.isEmpty()) {
            return CompletableFuture.completedFuture(HttpResponse.status(HttpStatus.NOT_FOUND).build());
        }
        return CompletableFuture.completedFuture(HttpResponse.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header("Allow", String.join(", ", allowed))
                .build());
    }

    private Optional<RouteMatch<HttpRouteHandler>> match(HttpMethod method, HttpRequest request) {
        RouteTable<HttpRouteHandler> table = routes.get(method);
        return table == null ? Optional.empty() : table.match(request.path());
    }

    private Set<String> allowedMethods(HttpRequest request) {
        Set<String> result = new TreeSet<>();
        routes.forEach((method, table) -> {
            if (table.match(request.path()).isPresent()) {
                result.add(method.name());
                if (method == HttpMethod.GET) {
                    result.add(HttpMethod.HEAD.name());
                }
            }
        });
        return result;
    }

    /** Mutable assembly DSL producing an immutable router. */
    public static final class Builder {
        private final Map<HttpMethod, List<Registration>> registrations = new EnumMap<>(HttpMethod.class);

        public Builder route(HttpMethod method, String template, HttpRouteHandler handler) {
            return route(method, RouteTemplate.parse(template), handler);
        }

        public Builder route(HttpMethod method, RouteTemplate template, HttpRouteHandler handler) {
            registrations.computeIfAbsent(Objects.requireNonNull(method, "method"), _ -> new ArrayList<>())
                    .add(new Registration(Objects.requireNonNull(template, "template"),
                            Objects.requireNonNull(handler, "handler")));
            return this;
        }

        public Builder get(String template, HttpRouteHandler handler) {
            return route(HttpMethod.GET, template, handler);
        }

        public Builder head(String template, HttpRouteHandler handler) {
            return route(HttpMethod.HEAD, template, handler);
        }

        public Builder post(String template, HttpRouteHandler handler) {
            return route(HttpMethod.POST, template, handler);
        }

        public Builder put(String template, HttpRouteHandler handler) {
            return route(HttpMethod.PUT, template, handler);
        }

        public Builder patch(String template, HttpRouteHandler handler) {
            return route(HttpMethod.PATCH, template, handler);
        }

        public Builder delete(String template, HttpRouteHandler handler) {
            return route(HttpMethod.DELETE, template, handler);
        }

        public Builder options(String template, HttpRouteHandler handler) {
            return route(HttpMethod.OPTIONS, template, handler);
        }

        public HttpRouter build() {
            Map<HttpMethod, RouteTable<HttpRouteHandler>> built = new EnumMap<>(HttpMethod.class);
            registrations.forEach((method, methodRoutes) -> {
                RouteTable.Builder<HttpRouteHandler> table = RouteTable.builder();
                methodRoutes.forEach(route -> table.route(route.template(), route.handler()));
                built.put(method, table.build());
            });
            return new HttpRouter(built);
        }
    }

    private record Registration(RouteTemplate template, HttpRouteHandler handler) {
    }
}
