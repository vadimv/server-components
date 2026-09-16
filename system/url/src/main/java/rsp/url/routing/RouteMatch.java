package rsp.url.routing;

import java.util.Objects;

/** The target and extracted values selected for a path. */
public record RouteMatch<T>(T target, RouteTemplate template, PathMatch path) {
    public RouteMatch {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(path, "path");
    }
}
