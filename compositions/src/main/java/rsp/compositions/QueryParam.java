package rsp.compositions;

import rsp.component.Lookup;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class QueryParam<T> {
    private final String name;
    private final Class<T> type;
    private final Function<String, T> converter;
    private final T defaultValue;

    public QueryParam(String name, Class<T> type, T defaultValue) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.converter = TypesConvertion.getBasicTypesConverter(type);
        this.defaultValue = defaultValue; // can be null
    }

    public QueryParam(String name, Class<T> type, Function<String, T> converter, T defaultValue) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.converter = Objects.requireNonNull(converter);
        this.defaultValue = defaultValue; // can be null
    }


    @SuppressWarnings("unchecked")
    public T resolve(Lookup lookup) {
        // Read from "url.query.{name}" namespace (populated by AutoAddressBarSyncComponent)
        final Object value = lookup.get(ContextKeys.URL_QUERY.with(name));

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

        // Parse list values and return the first element
        if (value instanceof List<?> list && !list.isEmpty()) {
            if (list.get(0) instanceof String s) {
                return converter.apply(s);
            }

            return defaultValue;
        }

        throw new IllegalStateException();
    }
}
