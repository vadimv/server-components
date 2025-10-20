package rsp.page;

import rsp.component.ComponentCompositeKey;

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
        final Object oldValue = map.get(key);
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


    public ComponentContext ofComponent(ComponentCompositeKey componentId) {
        return new ComponentContext() {
            @Override
            public void put(String key, Object value) {
                Lookup.this.put(key, value);
            }

            @Override
            public void remove(String key) {
                Lookup.this.remove(key);
            }

            @Override
            public Object get(String key) {
                return Lookup.this.get(key);
            }

            @Override
            public boolean containsKey(String key) {
                return Lookup.this.containsKey(key);
            }
        };
    }

    public interface ComponentContext {
        void put(String key, Object value);
        void remove(String key);
        Object get(String key);
        boolean containsKey(String key);
    }

}
