package rsp.compositions.routing;

import rsp.compositions.block.Block;

import rsp.server.Path;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Router - Maps URL paths to block component classes.
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
     * @param blockClass The block component class for this route
     * @param pattern The route pattern (e.g., "/posts/:id")
     */
    public record RouteMatch(Class<? extends Block<?, ?>> blockClass, String pattern) {}

    /**
     * Register a route pattern.
     *
     * @param path The path pattern (e.g., "/posts" or "/posts/:id")
     * @param blockClass The block component class to use for this route
     * @return this Router for chaining
     */
    public Router route(String path, Class<? extends Block<?, ?>> blockClass) {
        routes.put(path, new RoutePattern(path, blockClass));
        return this;
    }

    /**
     * Match an incoming URL path to a registered route.
     *
     * @param path The incoming URL path (e.g., Path of "/posts/123")
     * @return The matching route details (block class and pattern), or empty if no match
     */
    public Optional<RouteMatch> match(Path path) {
        // Try routes in registration order (LinkedHashMap preserves order)
        for (RoutePattern pattern : routes.values()) {
            if (pattern.matches(path)) {
                return Optional.of(new RouteMatch(pattern.blockClass(), pattern.pattern()));
            }
        }

        return Optional.empty();
    }

    /**
     * Check if a block class has a registered route.
     *
     * @param blockClass The block class to check
     * @return true if a route is registered for this block
     */
    public boolean hasRoute(Class<? extends Block<?, ?>> blockClass) {
        return routes.values().stream()
                .anyMatch(p -> p.blockClass().equals(blockClass));
    }

    /**
     * Find the route pattern for a given block class.
     * <p>
     * This enables framework-driven navigation: blocks emit intent (ACTION_SUCCESS),
     * framework derives the route from composition configuration.
     *
     * @param blockClass The block class to find
     * @return The route pattern (e.g., "/posts"), or empty if not found
     */
    public Optional<String> findRoutePattern(Class<? extends Block<?, ?>> blockClass) {
        for (RoutePattern pattern : routes.values()) {
            if (pattern.blockClass().equals(blockClass)) {
                return Optional.of(pattern.pattern());
            }
        }
        return Optional.empty();
    }

    /**
     * Find the parent route for a given pattern.
     * <p>
     * For example, "/posts/:id" has parent "/posts".
     * This is useful when an OVERLAY block is routed directly -
     * we need to find the PRIMARY block to use as the base.
     *
     * @param pattern The route pattern (e.g., "/posts/:id")
     * @return The parent route match, or empty if no parent route exists
     */
    public Optional<RouteMatch> findParentRoute(String pattern) {
        // Remove the last segment to get parent pattern
        int lastSlash = pattern.lastIndexOf('/');
        if (lastSlash <= 0) {
            return Optional.empty(); // No parent (e.g., "/" or "posts")
        }

        String parentPattern = pattern.substring(0, lastSlash);
        if (parentPattern.isEmpty()) {
            parentPattern = "/";
        }

        // Look for a route with this parent pattern
        RoutePattern parentRoute = routes.get(parentPattern);
        if (parentRoute != null) {
            return Optional.of(new RouteMatch(parentRoute.blockClass(), parentRoute.pattern()));
        }

        return Optional.empty();
    }

    /**
     * Build a URL path from a pattern by substituting parameter values.
     * <p>
     * Example: buildPath("/posts/:id", "123") → "/posts/123"
     *
     * @param pattern The route pattern with parameters
     * @param params The parameter values in order
     * @return The built URL path
     */
    public String buildPath(String pattern, String... params) {
        String result = pattern;
        int paramIndex = 0;
        while (result.contains(":") && paramIndex < params.length) {
            int start = result.indexOf(':');
            int end = result.indexOf('/', start);
            if (end == -1) end = result.length();
            result = result.substring(0, start) + params[paramIndex++] + result.substring(end);
        }
        return result;
    }

    /**
     * A route pattern that can match exact paths or paths with parameters.
     */
    private record RoutePattern(String pattern, Class<? extends Block<?, ?>> blockClass) {

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
