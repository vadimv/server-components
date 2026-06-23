package rsp.compositions.application;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class Services {
    private final Map<Class<?>, Object> services;

    public Services() {
        this.services = new HashMap<>();
    }

    public Services(Map<Class<?>, Object> services) {
        this.services = new HashMap<>(Objects.requireNonNull(services));
    }

    public Services service(Class<?> type, Object instance) {
        services.put(Objects.requireNonNull(type), Objects.requireNonNull(instance));
        return this;
    }

    public Map<Class<?>, Object> asMap() {
        return Collections.unmodifiableMap(services);
    }
}
