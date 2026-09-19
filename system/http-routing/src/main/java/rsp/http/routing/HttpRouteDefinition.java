package rsp.http.routing;

import rsp.http.HttpMethod;
import rsp.url.routing.RouteTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only route information for documentation and other build-time-style projections. */
public record HttpRouteDefinition(HttpMethod method,
                                  RouteTemplate template,
                                  List<HttpRouteMetadata> metadata) {
    public HttpRouteDefinition {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(template, "template");
        metadata = List.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }

    /** Returns the single metadata item of {@code type}, or fails if the route is ambiguous. */
    public <T extends HttpRouteMetadata> Optional<T> metadata(Class<T> type) {
        Objects.requireNonNull(type, "type");
        List<T> matches = metadata.stream().filter(type::isInstance).map(type::cast).toList();
        if (matches.size() > 1) {
            throw new IllegalStateException("Route " + method + " " + template
                    + " has duplicate metadata of type " + type.getName());
        }
        return matches.stream().findFirst();
    }
}
