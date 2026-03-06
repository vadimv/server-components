package rsp.compositions.agent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable attribute bag for ABAC policy evaluation.
 * <p>
 * Keys use namespace prefixes: {@code subject.*}, {@code resource.*}, {@code action.*},
 * {@code control.*}, {@code context.*}, {@code grant.*}.
 *
 * @param values the attribute map (defensively copied)
 */
public record Attributes(Map<String, Object> values) {

    public Attributes {
        Objects.requireNonNull(values);
        values = Map.copyOf(values);
    }

    public Object get(String key) {
        return values.get(key);
    }

    public String getString(String key) {
        Object v = values.get(key);
        return v instanceof String s ? s : null;
    }

    @SuppressWarnings("unchecked")
    public <T> T getTyped(String key, Class<T> type) {
        Object v = values.get(key);
        return type.isInstance(v) ? (T) v : null;
    }

    public boolean hasKey(String key) {
        return values.containsKey(key);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final HashMap<String, Object> map = new HashMap<>();

        public Builder put(String key, Object value) {
            if (value != null) {
                map.put(key, value);
            }
            return this;
        }

        public Attributes build() {
            return new Attributes(map);
        }
    }
}
