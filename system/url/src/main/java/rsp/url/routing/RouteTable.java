package rsp.url.routing;

import rsp.url.Path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** An immutable, deterministic mapping from route templates to arbitrary targets. */
public final class RouteTable<T> {
    private final List<Route<T>> routes;

    private RouteTable(List<Route<T>> routes) {
        this.routes = routes.stream()
                .sorted(Comparator.<Route<T>>comparingInt(route -> route.template().literalCount())
                        .reversed()
                        .thenComparing(route -> route.template().toString()))
                .toList();
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static <T> RouteTable<T> empty() {
        return new RouteTable<>(List.of());
    }

    public Optional<RouteMatch<T>> match(Path path) {
        Objects.requireNonNull(path, "path");
        for (Route<T> route : routes) {
            Optional<PathMatch> match = route.template().match(path);
            if (match.isPresent()) {
                return Optional.of(new RouteMatch<>(route.target(), route.template(), match.get()));
            }
        }
        return Optional.empty();
    }

    public boolean containsTarget(T target) {
        return routes.stream().anyMatch(route -> route.target().equals(target));
    }

    public Optional<RouteTemplate> templateFor(T target) {
        return routes.stream()
                .filter(route -> route.target().equals(target))
                .map(Route::template)
                .findFirst();
    }

    public Optional<RouteMatch<T>> parentOf(RouteTemplate template) {
        Objects.requireNonNull(template, "template");
        Optional<RouteTemplate> parent = template.parent();
        if (parent.isEmpty()) {
            return Optional.empty();
        }
        return routes.stream()
                .filter(route -> route.template().equals(parent.get()))
                .findFirst()
                .map(route -> new RouteMatch<>(route.target(), route.template(), PathMatch.EMPTY));
    }

    public List<Route<T>> routes() {
        return routes;
    }

    public record Route<T>(RouteTemplate template, T target) {
        public Route {
            Objects.requireNonNull(template, "template");
            Objects.requireNonNull(target, "target");
        }
    }

    public static final class Builder<T> {
        private final List<Route<T>> routes = new ArrayList<>();

        public Builder<T> route(String template, T target) {
            return route(RouteTemplate.parse(template), target);
        }

        public Builder<T> route(RouteTemplate template, T target) {
            routes.add(new Route<>(template, target));
            return this;
        }

        public RouteTable<T> build() {
            for (int left = 0; left < routes.size(); left++) {
                RouteTemplate leftTemplate = routes.get(left).template();
                for (int right = left + 1; right < routes.size(); right++) {
                    RouteTemplate rightTemplate = routes.get(right).template();
                    if (leftTemplate.literalCount() == rightTemplate.literalCount()
                            && leftTemplate.overlaps(rightTemplate)) {
                        throw new IllegalArgumentException("Ambiguous route templates: "
                                + leftTemplate + " and " + rightTemplate);
                    }
                }
            }
            return new RouteTable<>(List.copyOf(routes));
        }
    }
}
