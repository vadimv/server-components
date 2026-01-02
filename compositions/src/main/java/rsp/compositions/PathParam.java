package rsp.compositions;

import rsp.component.ComponentContext;

/**
 * PathParam - Helper for extracting typed path parameters from URL paths.
 * <p>
 * Works with {@link rsp.component.definitions.AutoAddressBarSyncComponent} which
 * populates context with {@code "url.path.{index}"} keys for each path segment.
 * <p>
 * Example:
 * <pre>{@code
 * // URL: /posts/123/edit
 * PathParam<String> postId = new PathParam<>(1, String.class, null);
 * String id = postId.resolve(context);  // Returns "123"
 * }</pre>
 *
 * @param <T> The type of the path parameter (String, Integer, Long, etc.)
 */
public class PathParam<T> {
    private final int index;
    private final Class<T> type;
    private final T defaultValue;

    /**
     * Create a path parameter extractor.
     *
     * @param index The 0-based index of the path segment (e.g., 1 for the second segment)
     * @param type The expected type of the parameter
     * @param defaultValue The default value if the path segment is missing or cannot be parsed
     */
    public PathParam(int index, Class<T> type, T defaultValue) {
        this.index = index;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    /**
     * Resolve the path parameter from the component context.
     *
     * @param ctx The component context containing URL path data
     * @return The parsed parameter value, or the default value if not present or parse fails
     */
    public T resolve(ComponentContext ctx) {
        // Read from "url.path.{index}" namespace (populated by AutoAddressBarSyncComponent)
        final String contextKey = "url.path." + index;
        final Object value = ctx.getAttribute(contextKey);

        if (value == null) {
            return defaultValue;
        }

        // If value is already the correct type, return it
        if (type.isAssignableFrom(value.getClass())) {
            return type.cast(value);
        }

        // Parse String values to the target type
        if (value instanceof String stringValue) {
            return parseStringValue(stringValue);
        }

        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private T parseStringValue(String stringValue) {
        try {
            if (type == Integer.class) {
                return (T) Integer.valueOf(stringValue);
            } else if (type == Long.class) {
                return (T) Long.valueOf(stringValue);
            } else if (type == Boolean.class) {
                return (T) Boolean.valueOf(stringValue);
            } else if (type == String.class) {
                return (T) stringValue;
            }
        } catch (NumberFormatException e) {
            // Return default on parse error
        }
        return defaultValue;
    }
}
