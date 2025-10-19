package rsp.page;

import rsp.component.ComponentCompositeKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class PageObjects {

    final Map<String, Object> map = new HashMap<>();


    public PageObjects() {
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
                PageObjects.this.put(key, value);
            }

            @Override
            public void remove(String key) {
                PageObjects.this.remove(key);
            }

            @Override
            public Object get(String key) {
                return PageObjects.this.get(key);
            }

            @Override
            public boolean containsKey(String key) {
                return PageObjects.this.containsKey(key);
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
