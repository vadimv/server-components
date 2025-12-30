package rsp.compositions;

import rsp.component.definitions.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class UiRegistry {
    private final Map<Class<? extends ViewContract>, UiComponent<?>> components = new HashMap<>();

    public <T extends ViewContract> UiRegistry register(Class<T> contractClass, UiComponent<T> component) {
        components.put(contractClass, component);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T extends ViewContract> Optional<UiComponent<T>> find(Class<T> contractClass) {
        // Simple lookup. In a real app, we might want to traverse the hierarchy to find a handler for a superclass.
        return Optional.ofNullable((UiComponent<T>) components.get(contractClass));
    }
    
    // Helper for hierarchy lookup
    @SuppressWarnings("unchecked")
    public <T extends ViewContract> Optional<UiComponent<T>> findFor(Class<? extends T> contractClass) {
        Class<?> current = contractClass;
        while (current != null && ViewContract.class.isAssignableFrom(current)) {
            if (components.containsKey(current)) {
                return Optional.of((UiComponent<T>) components.get(current));
            }
            current = current.getSuperclass();
        }
        return Optional.empty();
    }
}
