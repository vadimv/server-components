package rsp.compositions;

import rsp.component.ComponentContext;

public class QueryParam<T> {
    private final String name;
    private final Class<T> type;
    private final T defaultValue;

    public QueryParam(String name, Class<T> type, T defaultValue) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public T resolve(ComponentContext ctx) {
        // Read from "url.query.{name}" namespace (populated by AutoAddressBarSyncComponent)
        final String contextKey = "url.query." + name;
        final Object value = ctx.getAttribute(contextKey);

        if (value == null) {
            return defaultValue;
        }

        // If value is already the correct type, return it
        if (type.isAssignableFrom(value.getClass())) {
            return (T) value;
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
