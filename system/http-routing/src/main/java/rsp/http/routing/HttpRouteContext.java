package rsp.http.routing;

import rsp.url.routing.PathMatch;
import rsp.url.routing.RouteTemplate;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable metadata for the route selected for an HTTP request. */
public record HttpRouteContext(RouteTemplate template, PathMatch path) {
    public HttpRouteContext {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(path, "path");
    }

    /** Returns one decoded path parameter, if the template declares it. */
    public Optional<String> parameter(String name) {
        return Optional.ofNullable(path.parameters().get(Objects.requireNonNull(name, "name")));
    }

    /** Returns one decoded path parameter or fails when it is not declared. */
    public String requiredParameter(String name) {
        return path.required(name);
    }

    /** Returns every decoded path parameter. */
    public Map<String, String> parameters() {
        return path.parameters();
    }
}
