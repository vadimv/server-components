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
        final Object value = ctx.getAttribute(name);
        if (value == null) {
            return defaultValue;
        }
        
        if (type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        
        // Try to convert from String
        if (value instanceof String) {
            String strVal = (String) value;
            if (type == Integer.class) {
                try {
                    return (T) Integer.valueOf(strVal);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
            if (type == Long.class) {
                try {
                    return (T) Long.valueOf(strVal);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
            if (type == Boolean.class) {
                return (T) Boolean.valueOf(strVal);
            }
            // Add more types as needed
        }
        
        return defaultValue;
    }
}
