package rsp.compositions.contract;

import rsp.component.Lookup;

import java.util.Objects;
import java.util.function.Function;

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
 * String id = postId.resolve(lookup);  // Returns "123"
 * }</pre>
 *
 * @param <T> The type of the path parameter (String, Integer, Long, etc.)
 */
public class PathParam<T> {
    private final int index;
    private final Class<T> type;
    private final Function<String, T> converter;
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
        this.converter =  TypesConvertion.getBasicTypesConverter(type);
        this.defaultValue = defaultValue;
    }

    /**
     * Create a path parameter extractor with a specified convertor.
     *
     * @param index The 0-based index of the path segment (e.g., 1 for the second segment)
     * @param type The expected type of the parameter
     * @param converter
     * @param defaultValue The default value if the path segment is missing or cannot be parsed
     */
    public PathParam(int index, Class<T> type, Function<String, T> converter, T defaultValue) {
        this.index = Objects.requireNonNull(index);
        this.type = Objects.requireNonNull(type);
        this.converter = Objects.requireNonNull(converter);
        this.defaultValue = defaultValue;
    }

    /**
     * Resolve the path parameter from the lookup.
     *
     * @param lookup The lookup containing URL path data
     * @return The parsed parameter value, or the default value if not present or parse fails
     */
    @SuppressWarnings("unchecked")
    public T resolve(Lookup lookup) {
        // Read from "url.path.{index}" namespace (populated by AutoAddressBarSyncComponent)
        final Object value = lookup.get(ContextKeys.URL_PATH.with(String.valueOf(index)));

        if (value == null) {
            return defaultValue;
        }

        // If value is already the correct type, return it
        if (type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }

        // Parse String values to the target type
        if (value instanceof String stringValue) {
            return converter.apply(stringValue);
        }

        throw new IllegalStateException();
    }
}
