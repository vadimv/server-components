package rsp.page;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class Lookup {

    final Map<String, Object> map = new HashMap<>();

    public Lookup() {
    }

    /**
     *
     * @param key
     * @param value null is not allowed
     */
    public void put(String key, Object value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        map.put(key, value);
    }

    public void remove(String key) {
        Objects.requireNonNull(key);
        map.remove(key);
    }

    public Object get(String key) {
        return map.get(key);
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

}
