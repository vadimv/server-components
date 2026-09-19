package rsp.http;

import rsp.http.routing.HttpPrefixHandler;
import rsp.http.routing.HttpRouteDefinition;
import rsp.http.routing.HttpRouteHandler;
import rsp.http.routing.HttpRouteMetadata;
import rsp.url.routing.RouteTable;
import rsp.url.routing.RouteTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent router for composing ordinary HTTP endpoints and UI pages.
 *
 * <p>The built {@link Router} is a declaration graph. {@link WebServer}
 * supplies the UI renderer needed to turn {@link PageResult} values into HTTP
 * responses, while ordinary HTTP handlers remain independent of the UI.</p>
 */
public final class HttpRouter implements Router {
    private final rsp.http.routing.HttpRouter responseRoutes;
    private final List<ResultRegistration> resultRoutes;

    private HttpRouter(rsp.http.routing.HttpRouter responseRoutes,
                       List<ResultRegistration> resultRoutes) {
        this.responseRoutes = Objects.requireNonNull(responseRoutes, "responseRoutes");
        this.resultRoutes = List.copyOf(Objects.requireNonNull(resultRoutes, "resultRoutes"));
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<HttpRouteDefinition> routeDefinitions() {
        List<HttpRouteDefinition> definitions = new ArrayList<>(responseRoutes.routeDefinitions());
        resultRoutes.forEach(route -> definitions.add(new HttpRouteDefinition(
                route.method(), route.template(), route.metadata())));
        return definitions.stream()
                .sorted(Comparator.comparing((HttpRouteDefinition route) -> route.template().toString())
                        .thenComparing(route -> route.method().ordinal()))
                .toList();
    }

    rsp.http.routing.HttpRouter responseRoutes() {
        return responseRoutes;
    }

    List<ResultRegistration> resultRoutes() {
        return resultRoutes;
    }

    /** Mutable assembly DSL producing an immutable mixed route graph. */
    public static final class Builder {
        private final rsp.http.routing.HttpRouter.Builder responseRoutes =
                rsp.http.routing.HttpRouter.builder();
        private final List<ResultRegistration> resultRoutes = new ArrayList<>();

        public Builder route(HttpMethod method, String template, RouteHandler handler) {
            return route(method, template, handler, new HttpRouteMetadata[0]);
        }

        public Builder route(HttpMethod method,
                             String template,
                             RouteHandler handler,
                             HttpRouteMetadata... metadata) {
            return route(method, RouteTemplate.parse(template), handler, metadata);
        }

        public Builder asyncRoute(HttpMethod method,
                                  String template,
                                  HttpRouteHandler handler,
                                  HttpRouteMetadata... metadata) {
            responseRoutes.route(method, template, handler, metadata);
            return this;
        }

        public Builder route(HttpMethod method,
                             RouteTemplate template,
                             RouteHandler handler,
                             HttpRouteMetadata... metadata) {
            resultRoutes.add(new ResultRegistration(method, template, handler, List.of(metadata)));
            return this;
        }

        public Builder asyncRoute(HttpMethod method,
                                  RouteTemplate template,
                                  HttpRouteHandler handler,
                                  HttpRouteMetadata... metadata) {
            responseRoutes.route(method, template, handler, metadata);
            return this;
        }

        public Builder get(String template, RouteHandler handler) {
            return route(HttpMethod.GET, template, handler);
        }

        public Builder get(String template,
                           RouteHandler handler,
                           HttpRouteMetadata... metadata) {
            return route(HttpMethod.GET, template, handler, metadata);
        }

        public Builder getAsync(String template, HttpRouteHandler handler, HttpRouteMetadata... metadata) {
            return asyncRoute(HttpMethod.GET, template, handler, metadata);
        }

        public Builder head(String template, RouteHandler handler) {
            return route(HttpMethod.HEAD, template, handler);
        }

        public Builder head(String template,
                            RouteHandler handler,
                            HttpRouteMetadata... metadata) {
            return route(HttpMethod.HEAD, template, handler, metadata);
        }

        public Builder headAsync(String template, HttpRouteHandler handler, HttpRouteMetadata... metadata) {
            return asyncRoute(HttpMethod.HEAD, template, handler, metadata);
        }

        public Builder post(String template, RouteHandler handler) {
            return route(HttpMethod.POST, template, handler);
        }

        public Builder post(String template,
                            RouteHandler handler,
                            HttpRouteMetadata... metadata) {
            return route(HttpMethod.POST, template, handler, metadata);
        }

        public Builder postAsync(String template, HttpRouteHandler handler, HttpRouteMetadata... metadata) {
            return asyncRoute(HttpMethod.POST, template, handler, metadata);
        }

        public Builder put(String template, RouteHandler handler) {
            return route(HttpMethod.PUT, template, handler);
        }

        public Builder put(String template,
                           RouteHandler handler,
                           HttpRouteMetadata... metadata) {
            return route(HttpMethod.PUT, template, handler, metadata);
        }

        public Builder putAsync(String template, HttpRouteHandler handler, HttpRouteMetadata... metadata) {
            return asyncRoute(HttpMethod.PUT, template, handler, metadata);
        }

        public Builder patch(String template, RouteHandler handler) {
            return route(HttpMethod.PATCH, template, handler);
        }

        public Builder patch(String template,
                             RouteHandler handler,
                             HttpRouteMetadata... metadata) {
            return route(HttpMethod.PATCH, template, handler, metadata);
        }

        public Builder patchAsync(String template, HttpRouteHandler handler, HttpRouteMetadata... metadata) {
            return asyncRoute(HttpMethod.PATCH, template, handler, metadata);
        }

        public Builder delete(String template, RouteHandler handler) {
            return route(HttpMethod.DELETE, template, handler);
        }

        public Builder delete(String template,
                              RouteHandler handler,
                              HttpRouteMetadata... metadata) {
            return route(HttpMethod.DELETE, template, handler, metadata);
        }

        public Builder deleteAsync(String template, HttpRouteHandler handler, HttpRouteMetadata... metadata) {
            return asyncRoute(HttpMethod.DELETE, template, handler, metadata);
        }

        public Builder options(String template, RouteHandler handler) {
            return route(HttpMethod.OPTIONS, template, handler);
        }

        public Builder options(String template,
                               RouteHandler handler,
                               HttpRouteMetadata... metadata) {
            return route(HttpMethod.OPTIONS, template, handler, metadata);
        }

        public Builder optionsAsync(String template, HttpRouteHandler handler, HttpRouteMetadata... metadata) {
            return asyncRoute(HttpMethod.OPTIONS, template, handler, metadata);
        }

        /** Registers a literal HTTP-response path prefix. */
        public Builder prefix(HttpMethod method, String pathPrefix, HttpPrefixHandler handler) {
            responseRoutes.prefix(method, pathPrefix, handler);
            return this;
        }

        public Builder prefix(HttpMethod method,
                              String pathPrefix,
                              HttpPrefixHandler.Synchronous handler) {
            return prefix(method, pathPrefix, HttpPrefixHandler.sync(handler));
        }

        public Builder getPrefix(String pathPrefix, HttpPrefixHandler handler) {
            return prefix(HttpMethod.GET, pathPrefix, handler);
        }

        public Builder getPrefix(String pathPrefix, HttpPrefixHandler.Synchronous handler) {
            return prefix(HttpMethod.GET, pathPrefix, handler);
        }

        /** Includes another mixed route graph. */
        public Builder include(Router router) {
            HttpRouter routes = (HttpRouter) Objects.requireNonNull(router, "router");
            responseRoutes.include(routes.responseRoutes);
            resultRoutes.addAll(routes.resultRoutes);
            return this;
        }

        /** Includes routes produced by a UI-independent HTTP library. */
        public Builder include(rsp.http.routing.HttpRouter router) {
            responseRoutes.include(Objects.requireNonNull(router, "router"));
            return this;
        }

        public HttpRouter build() {
            rsp.http.routing.HttpRouter builtResponses = responseRoutes.build();
            validateCombinedRoutes(builtResponses, resultRoutes);
            return new HttpRouter(builtResponses, resultRoutes);
        }

        private static void validateCombinedRoutes(rsp.http.routing.HttpRouter responses,
                                                   List<ResultRegistration> results) {
            Map<HttpMethod, RouteTable.Builder<Object>> byMethod = new EnumMap<>(HttpMethod.class);
            for (HttpRouteDefinition definition : responses.routeDefinitions()) {
                byMethod.computeIfAbsent(definition.method(), _ -> RouteTable.builder())
                        .route(definition.template(), definition);
            }
            for (ResultRegistration result : results) {
                byMethod.computeIfAbsent(result.method(), _ -> RouteTable.builder())
                        .route(result.template(), result);
            }
            byMethod.values().forEach(RouteTable.Builder::build);
        }
    }

    record ResultRegistration(HttpMethod method,
                              RouteTemplate template,
                              RouteHandler handler,
                              List<HttpRouteMetadata> metadata) {
        ResultRegistration {
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(template, "template");
            Objects.requireNonNull(handler, "handler");
            metadata = List.copyOf(Objects.requireNonNull(metadata, "metadata"));
        }
    }
}
