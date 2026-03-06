package rsp.compositions.application;

import rsp.component.ContextKey;
import rsp.component.EventKey;
import rsp.component.Lookup;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Minimal read-only Lookup backed by a service map.
 * Used for app-level LifecycleHandler callbacks where no full component context is available.
 */
final class ServiceMapLookup implements Lookup {

    private final Map<Class<?>, Object> services;

    ServiceMapLookup(Map<Class<?>, Object> services) {
        this.services = services;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clazz) {
        return (T) services.get(clazz);
    }

    @Override
    public <T> T get(ContextKey<T> key) {
        return null;
    }

    @Override
    public <T> T getRequired(ContextKey<T> key) {
        throw new IllegalStateException("Key not available in app-level lookup: " + key);
    }

    @Override
    public <T> T getRequired(Class<T> clazz) {
        T value = get(clazz);
        if (value == null) {
            throw new IllegalStateException("Service not found: " + clazz.getName());
        }
        return value;
    }

    @Override
    public <T> Lookup with(ContextKey<T> key, T value) {
        throw new UnsupportedOperationException("App-level lookup is read-only");
    }

    @Override
    public <T> Lookup with(Class<T> clazz, T instance) {
        throw new UnsupportedOperationException("App-level lookup is read-only");
    }

    @Override
    public <T> Registration subscribe(EventKey<T> key, BiConsumer<String, T> handler) {
        throw new UnsupportedOperationException("Event subscription not available in app-level lookup");
    }

    @Override
    public Registration subscribe(EventKey.VoidKey key, Runnable handler) {
        throw new UnsupportedOperationException("Event subscription not available in app-level lookup");
    }

    @Override
    public <T> void publish(EventKey<T> key, T payload) {
        throw new UnsupportedOperationException("Event publishing not available in app-level lookup");
    }

    @Override
    public void publish(EventKey.VoidKey key) {
        throw new UnsupportedOperationException("Event publishing not available in app-level lookup");
    }
}
