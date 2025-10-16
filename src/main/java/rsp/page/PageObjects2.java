package rsp.page;

import rsp.component.ComponentCompositeKey;
import rsp.page.events.GenericTaskEvent;
import rsp.page.events.SessionEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class PageObjects2 {

    final Map<String, Object> map = new HashMap<>();
    final Map<ComponentCompositeKey, Map<String, Consumer<Object>>> componentsOnValueUpdatedCallbacks = new HashMap<>();
    final Map<ComponentCompositeKey, Map<String, Runnable>> componentsOnRemovedCallbacks = new HashMap<>();

    private final Consumer<SessionEvent> commandsEnqueue;

    public PageObjects2(Consumer<SessionEvent> commandsEnqueue) {
        this.commandsEnqueue = Objects.requireNonNull(commandsEnqueue);
    }

    /**
     *
     * @param key
     * @param value null is not allowed
     */
    public void put(String key, Object value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        commandsEnqueue.accept(new GenericTaskEvent(() -> {
            final Object oldValue = map.get(key);
            if (!value.equals(oldValue)) {
                map.put(key, Objects.requireNonNull(value));
                for (ComponentCompositeKey componentId : componentsOnValueUpdatedCallbacks.keySet()) {
                    final Map<String, Consumer<Object>> onValueUpdatedCallbacks = componentsOnValueUpdatedCallbacks.get(componentId);
                    if (onValueUpdatedCallbacks != null) {
                        final Consumer<Object> callback = onValueUpdatedCallbacks.get(key);
                        if (callback != null) {
                            commandsEnqueue.accept(new GenericTaskEvent(() -> callback.accept(value)));
                        }
                    }
                }
            }
        }));
    }

    public void remove(String key) {
        Objects.requireNonNull(key);
        commandsEnqueue.accept(new GenericTaskEvent(() -> {
            final Object oldValue = map.get(key);
            if (oldValue != null) {
                map.remove(key);
                for (ComponentCompositeKey componentId : componentsOnRemovedCallbacks.keySet()) {
                    final Map<String, Runnable> onRemovedCallbacks = componentsOnRemovedCallbacks.get(componentId);
                    if (onRemovedCallbacks != null) {
                        final Runnable callback = onRemovedCallbacks.get(key);
                        if (callback != null) {
                            commandsEnqueue.accept(new GenericTaskEvent(callback));
                        }
                    }
                }
            }
        }));
    }

    public Object get(String key) {
        return map.get(key);
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    public void onValueUpdated(String key, ComponentCompositeKey componentId, Consumer<Object> callback) {
        commandsEnqueue.accept(new GenericTaskEvent(() -> {
            final Map<String, Consumer<Object>> onValueUpdatedCallbacks;
            if (componentsOnValueUpdatedCallbacks.containsKey(componentId)) {
                onValueUpdatedCallbacks = componentsOnValueUpdatedCallbacks.get(componentId);
            } else {
                onValueUpdatedCallbacks = new HashMap<>();
                componentsOnValueUpdatedCallbacks.put(componentId, onValueUpdatedCallbacks);
            }
            onValueUpdatedCallbacks.put(key, callback);
        }));
    }

    public void onValueRemoved(String key, ComponentCompositeKey componentId, Runnable callback) {
        commandsEnqueue.accept(new GenericTaskEvent(() -> {
            final Map<String, Runnable> onRemovedCallbacks;
            if (componentsOnRemovedCallbacks.containsKey(componentId)) {
                onRemovedCallbacks = componentsOnRemovedCallbacks.get(componentId);
            } else {
                onRemovedCallbacks = new HashMap<>();
            }
            onRemovedCallbacks.put(key, callback);
        }));
    }

    public void removeCallbacks(String key, ComponentCompositeKey componentId) {
        commandsEnqueue.accept(new GenericTaskEvent(() -> {
            var onValueUpdatedCallbacks = componentsOnValueUpdatedCallbacks.get(componentId);
            if (onValueUpdatedCallbacks != null) {
                onValueUpdatedCallbacks.remove(key);
            }
        }));
    }

    public void removeCallbacks(ComponentCompositeKey componentId) {
        commandsEnqueue.accept(new GenericTaskEvent(() -> {
            componentsOnValueUpdatedCallbacks.remove(componentId);
            componentsOnRemovedCallbacks.remove(componentId);
        }));
    }

    public ComponentContext ofComponent(ComponentCompositeKey componentId) {
        return new ComponentContext() {
            @Override
            public void put(String key, Object value) {
                PageObjects2.this.put(key, value);
            }

            @Override
            public void remove(String key) {
                PageObjects2.this.remove(key);
            }

            @Override
            public Object get(String key) {
                return PageObjects2.this.get(key);
            }

            @Override
            public boolean containsKey(String key) {
                return PageObjects2.this.containsKey(key);
            }

            @Override
            public void onValueUpdated(String key, Consumer<Object> callback) {
                PageObjects2.this.onValueUpdated(key, componentId, callback);
            }

            @Override
            public void onValueRemoved(String key, ComponentCompositeKey componentId, Runnable callback) {
                PageObjects2.this.onValueRemoved(key, componentId, callback);
            }

            @Override
            public void removeCallbacks(String key) {
                PageObjects2.this.removeCallbacks(key, componentId);
            }

            @Override
            public void removeCallbacks() {
                PageObjects2.this.removeCallbacks(componentId);
            }
        };
    }

    public interface ComponentContext {
        void put(String key, Object value);
        void remove(String key);
        Object get(String key);
        boolean containsKey(String key);
        void onValueUpdated(String key, Consumer<Object> callback);
        void onValueRemoved(String key, ComponentCompositeKey componentId, Runnable callback);
        void removeCallbacks(String key);
        void removeCallbacks();
    }

}
