package rsp.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable typed service registry and owner of application-scoped resources. */
public final class ApplicationContext implements ApplicationLifecycle {
    public enum State {
        NEW,
        RUNNING,
        STOPPED
    }

    private final ApplicationConfig config;
    private final Map<Class<?>, Object> services;
    private final List<ApplicationLifecycle> managedServices;
    private State state = State.NEW;

    private ApplicationContext(ApplicationConfig config, Map<Class<?>, Object> services) {
        this.config = Objects.requireNonNull(config, "config");
        this.services = Collections.unmodifiableMap(new LinkedHashMap<>(services));
        this.managedServices = managedServices(services.values());
    }

    public static Builder builder() {
        return new Builder();
    }

    public ApplicationConfig config() {
        return config;
    }

    public Map<Class<?>, Object> services() {
        return services;
    }

    public <T> T get(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return type.cast(services.get(type));
    }

    public <T> T require(Class<T> type) {
        T service = get(type);
        if (service == null) {
            throw new IllegalStateException("Application service not found: " + type.getName());
        }
        return service;
    }

    public synchronized State state() {
        return state;
    }

    @Override
    public synchronized void start() {
        if (state == State.RUNNING) {
            return;
        }
        if (state == State.STOPPED) {
            throw new IllegalStateException("Application context cannot be restarted after stop");
        }
        List<ApplicationLifecycle> started = new ArrayList<>();
        try {
            for (ApplicationLifecycle service : managedServices) {
                started.add(service);
                service.start();
            }
            state = State.RUNNING;
        } catch (RuntimeException | Error failure) {
            stopReverse(started, failure);
            state = State.STOPPED;
            throw failure;
        }
    }

    @Override
    public synchronized void stop() {
        if (state == State.STOPPED) {
            return;
        }
        if (state == State.NEW) {
            state = State.STOPPED;
            return;
        }
        Throwable failure = stopReverse(managedServices, null);
        state = State.STOPPED;
        rethrow(failure);
    }

    private static List<ApplicationLifecycle> managedServices(Iterable<Object> services) {
        List<ApplicationLifecycle> result = new ArrayList<>();
        IdentityHashMap<ApplicationLifecycle, Boolean> seen = new IdentityHashMap<>();
        for (Object service : services) {
            if (service instanceof ApplicationLifecycle lifecycle && seen.put(lifecycle, Boolean.TRUE) == null) {
                result.add(lifecycle);
            }
        }
        return List.copyOf(result);
    }

    private static Throwable stopReverse(List<ApplicationLifecycle> services, Throwable primary) {
        Throwable result = primary;
        for (int index = services.size() - 1; index >= 0; index--) {
            try {
                services.get(index).stop();
            } catch (RuntimeException | Error failure) {
                if (result == null) {
                    result = failure;
                } else {
                    result.addSuppressed(failure);
                }
            }
        }
        return result;
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    /** Mutable assembly DSL producing an immutable application context. */
    public static final class Builder {
        private ApplicationConfig config = ApplicationConfig.EMPTY;
        private final Map<Class<?>, Object> services = new LinkedHashMap<>();

        public Builder config(ApplicationConfig config) {
            this.config = Objects.requireNonNull(config, "config");
            return this;
        }

        public <T> Builder service(Class<T> type, T service) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(service, "service");
            if (!type.isInstance(service)) {
                throw new IllegalArgumentException(service.getClass().getName()
                        + " is not an instance of " + type.getName());
            }
            if (services.putIfAbsent(type, service) != null) {
                throw new IllegalArgumentException("Application service already registered: " + type.getName());
            }
            return this;
        }

        public ApplicationContext build() {
            return new ApplicationContext(config, services);
        }
    }
}
