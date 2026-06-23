package rsp.compositions.contract;

import rsp.component.ComponentContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class QueryParams<T> {
    private final String name;
    private final Class<T> type;
    private final Function<String, T> converter;
    private final List<T> defaultValue;

    public QueryParams(String name, Class<T> type, List<T> defaultValue) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.converter = TypesConvertion.getBasicTypesConverter(type);
        this.defaultValue = Objects.requireNonNull(defaultValue);
    }

    public QueryParams(String name, Class<T> type, Function<String, T> converter, List<T> defaultValue) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.converter = Objects.requireNonNull(converter);
        this.defaultValue = Objects.requireNonNull(defaultValue);
    }


    @SuppressWarnings("unchecked")
    public List<T> resolve(ComponentContext ctx) {
        // Read from "url.query.{name}" namespace (populated by AutoAddressBarSyncComponent)
        final Object value = ctx.get(ContextKeys.URL_QUERY.with(name));

        if (value == null) {
            return defaultValue;
        }

        if (value instanceof String s) {
            return List.of(converter.apply(s));
        }

        // Parse list values to the target type
        if (value instanceof List<?> list) {
            final List<T> result = new ArrayList<>();
            for (Object element : list) {
                if (element instanceof String e) {
                    result.add(converter.apply(e));
                }
            }
            return result;
        }

        throw new IllegalStateException();
    }
}
