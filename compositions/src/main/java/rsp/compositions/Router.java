package rsp.compositions;

import rsp.server.Path;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Router - Maps URL paths to ViewContract classes.
 * <p>
 * Supports both exact routes and path parameter routes:
 * <ul>
 *   <li>Exact: {@code "/posts"} matches only {@code "/posts"}</li>
 *   <li>Path params: {@code "/posts/:id"} matches {@code "/posts/123"}, {@code "/posts/abc"}, etc.</li>
 * </ul>
 */
public class Router {
    private final Map<String, RoutePattern> routes = new LinkedHashMap<>();

    /**
     * Result of matching a route.
     *
     * @param contractClass The ViewContract class for this route
     * @param pattern The route pattern (e.g., "/posts/:id")
     */
    public record RouteMatch(Class<? extends ViewContract> contractClass, String pattern) {}

    /**
     * Register a route pattern.
     *
     * @param path The path pattern (e.g., "/posts" or "/posts/:id")
     * @param contractClass The ViewContract class to use for this route
     * @return this Router for chaining
     */
    public Router route(String path, Class<? extends ViewContract> contractClass) {
        routes.put(path, new RoutePattern(path, contractClass));
        return this;
    }

    /**
     * Match an incoming URL path to a registered route.
     *
     * @param path The incoming URL path (e.g., Path of "/posts/123")
     * @return The matching route details (contract class and pattern), or empty if no match
     */
    public Optional<RouteMatch> match(Path path) {
        // Try routes in registration order (LinkedHashMap preserves order)
        for (RoutePattern pattern : routes.values()) {
            if (pattern.matches(path)) {
                return Optional.of(new RouteMatch(pattern.contractClass(), pattern.pattern()));
            }
        }

        return Optional.empty();
    }

    /**
     * A route pattern that can match exact paths or paths with parameters.
     */
    private record RoutePattern(String pattern, Class<? extends ViewContract> contractClass) {

        /**
         * Check if this pattern matches the given path.
         *
         * @param path The actual URL path as a Path object
         * @return true if the path matches this pattern
         */
        boolean matches(Path path) {
            // Split pattern into segments (skip empty first segment from leading /)
            String[] patternSegments = pattern.split("/");

            // Count non-empty pattern segments
            int patternCount = 0;
            for (String seg : patternSegments) {
                if (!seg.isEmpty()) patternCount++;
            }

            // Different number of segments -> no match
            if (patternCount != path.elementsCount()) {
                return false;
            }

            // Check each segment
            int pathIndex = 0;
            for (String patternSegment : patternSegments) {
                if (patternSegment.isEmpty()) continue;

                String pathSegment = path.get(pathIndex++);

                // Path parameter (starts with :) -> matches any value
                if (patternSegment.startsWith(":")) {
                    continue;
                }

                // Exact segment -> must match exactly
                if (!patternSegment.equals(pathSegment)) {
                    return false;
                }
            }

            return true;
        }
    }
}
