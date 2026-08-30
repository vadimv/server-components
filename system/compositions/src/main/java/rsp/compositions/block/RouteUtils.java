package rsp.compositions.block;

import rsp.component.Lookup;
import rsp.server.Path;
import rsp.server.http.Fragment;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;

import java.util.ArrayList;
import java.util.List;

/**
 * RouteUtils - Generic utilities for building routes.
 * <p>
 * Provides URL building logic without application-specific knowledge.
 * No knowledge of specific operations or views - purely generic framework utilities.
 */
public class RouteUtils {

    /**
     * Build parent route by stripping last segment.
     * <p>
     * Generic - works for any route pattern.
     * Convention: remove last segment if it's a parameter (starts with :) or a token (new, create).
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code "/posts/:id" → "/posts"}</li>
     *   <li>{@code "/posts/new" → "/posts"}</li>
     *   <li>{@code "/posts/:id" with fromQuery=p%3D3%26sort%3Dtitle →
     *       "/posts?p=3&sort=title"}</li>
     * </ul>
     *
     * @param routePattern The route pattern (e.g., "/posts/:id")
     * @param lookup Lookup for reading query parameters
     * @return The parent route URL (path + restored query + empty fragment)
     */
    public static RelativeUrl buildParentRoute(String routePattern, Lookup lookup) {
        Path basePath = Path.of(stripLastSegment(routePattern));
        Query restoredQuery = buildRestoredQuery(lookup);
        return new RelativeUrl(basePath, restoredQuery, Fragment.EMPTY);
    }

    /**
     * Strip last segment from route pattern if it's a parameter or token.
     *
     * @param routePattern The route pattern
     * @return The route pattern without the last segment
     */
    private static String stripLastSegment(String routePattern) {
        int lastSlash = routePattern.lastIndexOf('/');
        if (lastSlash > 0) {
            String lastSegment = routePattern.substring(lastSlash + 1);

            // Strip last segment if it's a parameter (starts with :) or a token
            if (lastSegment.startsWith(":") || isPathToken(lastSegment)) {
                return routePattern.substring(0, lastSlash);
            }
        }
        return routePattern;
    }

    /**
     * Check if a path segment is a recognized token (e.g., "new", "create").
     * <p>
     * Override this method to recognize custom tokens.
     *
     * @param segment The path segment to check
     * @return true if it's a token that should be stripped for list route
     */
    private static boolean isPathToken(String segment) {
        return "new".equals(segment) || "create".equals(segment);
    }

    /**
     * Build the parent query from an encoded {@code fromQuery} value.
     * <p>
     * The older conventions {@code fromP → p} and {@code fromSort → sort}
     * remain as a fallback for existing links.
     *
     * @param lookup Lookup for reading query parameters
     * @return Query with restored parameters (may be empty)
     */
    private static Query buildRestoredQuery(Lookup lookup) {
        String fromQuery = lookup.get(ContextKeys.URL_QUERY.with("fromQuery"));
        if (fromQuery != null && !fromQuery.isBlank()) {
            return Query.of(fromQuery);
        }
        List<Query.Parameter> params = new ArrayList<>();

        String fromP = lookup.get(ContextKeys.URL_QUERY.with("fromP"));
        if (fromP != null && !fromP.isEmpty()) {
            params.add(new Query.Parameter("p", fromP));
        }

        String fromSort = lookup.get(ContextKeys.URL_QUERY.with("fromSort"));
        if (fromSort != null && !fromSort.isEmpty()) {
            params.add(new Query.Parameter("sort", fromSort));
        }

        return params.isEmpty() ? Query.EMPTY : new Query(params);
    }
}
