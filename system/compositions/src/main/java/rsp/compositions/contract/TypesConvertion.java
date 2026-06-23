package rsp.compositions.contract;

import java.util.function.Function;

public class TypesConvertion {
    @SuppressWarnings("unchecked")
    public static <T> Function<String, T> getBasicTypesConverter(Class type) {

        if (type == Integer.class) {
            return s -> (T) Integer.valueOf(s);
        } else if (type == Long.class) {
            return s -> (T) Long.valueOf(s);
        } else if (type == Boolean.class) {
            return s -> (T) Boolean.valueOf(s);
        } else if (type == String.class) {
            return s -> (T) s;
        }
        throw new IllegalStateException();

    }
}
