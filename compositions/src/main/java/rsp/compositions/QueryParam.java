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

    // The framework calls this to fetch value from the current Context
    // (Assumes ViewContract has access to the current request Context)
    public T resolve(ComponentContext ctx) {
        final Object value = ctx.getAttribute(name);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        } else {
            return defaultValue;
        }

    }
}
