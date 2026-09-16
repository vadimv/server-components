package rsp.metrics;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe in-process registry for aggregate metrics and explicitly allowed,
 * lifecycle-bound metric objects. It is backed only by JDK classes.
 *
 * <p>Unknown names, mismatched update types, negative counter updates, unknown
 * object types, and opens beyond the configured capacity are ignored and
 * reflected in the framework's rejection counters when those counters are in
 * the aggregate catalog.</p>
 */
public final class MetricRegistry implements Metrics {
    /** Default defense-in-depth limit for simultaneously live metric objects. */
    public static final int DEFAULT_MAX_ACTIVE_METRIC_OBJECTS = 1_024;

    private final MetricCatalog catalog;
    private final MetricObjectCatalog objectCatalog;
    private final int maxActiveMetricObjects;
    private final Clock clock;
    private final Instant startedAt;
    private final Map<String, LongAdder> counters;
    private final Map<String, AtomicLong> gauges;

    private final Object objectLifecycleLock = new Object();
    private final Map<MetricObjectKey, LiveMetricObject> activeObjects = new LinkedHashMap<>();
    private final Map<String, Long> nextInstanceNumbers = new LinkedHashMap<>();
    private final List<MetricObjectListener> objectListeners = new ArrayList<>();
    private boolean objectLifecycleClosed;

    /** Creates a registry with framework-owned object types using the system UTC clock. */
    public MetricRegistry(final MetricCatalog catalog) {
        this(catalog,
             MetricObjectTypes.frameworkCatalog(),
             DEFAULT_MAX_ACTIVE_METRIC_OBJECTS,
             Clock.systemUTC());
    }

    /** Creates a registry with lifecycle-bound object support. */
    public MetricRegistry(final MetricCatalog catalog,
                          final MetricObjectCatalog objectCatalog) {
        this(catalog,
             objectCatalog,
             DEFAULT_MAX_ACTIVE_METRIC_OBJECTS,
             Clock.systemUTC());
    }

    /** Creates a registry with lifecycle-bound object support and an active-object limit. */
    public MetricRegistry(final MetricCatalog catalog,
                          final MetricObjectCatalog objectCatalog,
                          final int maxActiveMetricObjects) {
        this(catalog, objectCatalog, maxActiveMetricObjects, Clock.systemUTC());
    }

    MetricRegistry(final MetricCatalog catalog, final Clock clock) {
        this(catalog,
             MetricObjectTypes.frameworkCatalog(),
             DEFAULT_MAX_ACTIVE_METRIC_OBJECTS,
             clock);
    }

    MetricRegistry(final MetricCatalog catalog,
                   final MetricObjectCatalog objectCatalog,
                   final int maxActiveMetricObjects,
                   final Clock clock) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.objectCatalog = Objects.requireNonNull(objectCatalog, "objectCatalog");
        validateDistinctMetricNames(catalog, objectCatalog);
        if (maxActiveMetricObjects <= 0) {
            throw new IllegalArgumentException("Maximum active metric objects must be positive");
        }
        this.maxActiveMetricObjects = maxActiveMetricObjects;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.startedAt = clock.instant();

        final Map<String, LongAdder> newCounters = new LinkedHashMap<>();
        final Map<String, AtomicLong> newGauges = new LinkedHashMap<>();
        populateStores(catalog, newCounters, newGauges);
        counters = Map.copyOf(newCounters);
        gauges = Map.copyOf(newGauges);
    }

    private static void validateDistinctMetricNames(final MetricCatalog aggregateCatalog,
                                                    final MetricObjectCatalog metricObjectCatalog) {
        for (final MetricObjectType type : metricObjectCatalog.types()) {
            for (final MetricDescriptor descriptor : type.metrics().descriptors()) {
                if (aggregateCatalog.descriptor(descriptor.name()).isPresent()) {
                    throw new IllegalArgumentException(
                            "Metric name used by both aggregate and object catalogs: " + descriptor.name());
                }
            }
        }
    }

    @Override
    public void incrementCounter(final String name) {
        incrementCounter(name, 1);
    }

    @Override
    public void incrementCounter(final String name, final long delta) {
        if (!increment(counters, name, delta)) {
            rejectUpdate();
        }
    }

    @Override
    public void setGauge(final String name, final long value) {
        if (!set(gauges, name, value)) {
            rejectUpdate();
        }
    }

    @Override
    public MetricObject openObject(final MetricObjectType requestedType) {
        Objects.requireNonNull(requestedType, "requestedType");
        synchronized (objectLifecycleLock) {
            final MetricObjectType allowedType = objectCatalog.type(requestedType.name()).orElse(null);
            final long instanceNumber = allowedType == null
                    ? 0
                    : nextInstanceNumbers.getOrDefault(allowedType.name(), 0L);
            if (objectLifecycleClosed
                    || allowedType == null
                    || !allowedType.equals(requestedType)
                    || activeObjects.size() >= maxActiveMetricObjects
                    || instanceNumber == Long.MAX_VALUE) {
                rejectObjectOpen();
                return MetricObject.noop(requestedType);
            }

            nextInstanceNumbers.put(allowedType.name(), instanceNumber + 1);
            final LiveMetricObject object = new LiveMetricObject(allowedType, instanceNumber, clock.instant());
            activeObjects.put(new MetricObjectKey(allowedType.name(), instanceNumber), object);
            incrementInternal(MetricNames.METRIC_OBJECTS_CREATED);
            setInternal(MetricNames.METRIC_OBJECTS_ACTIVE, activeObjects.size());
            notifyObjectOpened(object);
            return object;
        }
    }

    /** Returns the catalog used to validate aggregate updates. */
    public MetricCatalog catalog() {
        return catalog;
    }

    /** Returns the fixed catalog of lifecycle-bound object types. */
    public MetricObjectCatalog objectCatalog() {
        return objectCatalog;
    }

    /** Returns one current aggregate value, failing for an unknown name. */
    public long value(final String name) {
        return currentValue(counters, gauges, name);
    }

    /** Copies all current aggregate values in stable catalog order. */
    public MetricSnapshot snapshot() {
        return snapshot(catalog, counters, gauges, startedAt);
    }

    /** Copies all currently live metric objects in creation order. */
    public List<MetricObjectSnapshot> metricObjectSnapshots() {
        synchronized (objectLifecycleLock) {
            return activeObjects.values().stream()
                    .map(object -> new MetricObjectSnapshot(
                            object.type(), object.instanceNumber(), object.snapshot()))
                    .toList();
        }
    }

    /** Returns the number of currently live metric objects. */
    public int activeMetricObjectCount() {
        synchronized (objectLifecycleLock) {
            return activeObjects.size();
        }
    }

    /**
     * Registers an adapter for future object open/close events. Existing objects
     * are available through {@link #metricObjectSnapshots()} and are not replayed.
     */
    public MetricObjectListener.Registration registerMetricObjectListener(
            final MetricObjectListener listener) {
        Objects.requireNonNull(listener, "listener");
        final AtomicBoolean registrationClosed = new AtomicBoolean();
        synchronized (objectLifecycleLock) {
            if (!objectLifecycleClosed) {
                objectListeners.add(listener);
            } else {
                registrationClosed.set(true);
            }
        }
        return () -> {
            if (registrationClosed.compareAndSet(false, true)) {
                synchronized (objectLifecycleLock) {
                    objectListeners.remove(listener);
                }
            }
        };
    }

    /**
     * Prevents new metric objects and closes all live objects. Aggregate metrics
     * remain readable. This method is idempotent.
     */
    public void closeMetricObjects() {
        synchronized (objectLifecycleLock) {
            if (objectLifecycleClosed) {
                return;
            }
            objectLifecycleClosed = true;
            for (final LiveMetricObject object : List.copyOf(activeObjects.values())) {
                closeObject(object);
            }
        }
    }

    private void closeObject(final LiveMetricObject object) {
        synchronized (objectLifecycleLock) {
            if (!object.markClosed()) {
                return;
            }
            activeObjects.remove(object.key(), object);
            setInternal(MetricNames.METRIC_OBJECTS_ACTIVE, activeObjects.size());
            notifyObjectClosed(object);
        }
    }

    private void notifyObjectOpened(final MetricObject object) {
        for (final MetricObjectListener listener : List.copyOf(objectListeners)) {
            try {
                listener.onOpened(object);
            } catch (final RuntimeException ignored) {
                incrementInternal(MetricNames.METRIC_OBJECT_ADAPTER_FAILURES);
            }
        }
    }

    private void notifyObjectClosed(final MetricObject object) {
        for (final MetricObjectListener listener : List.copyOf(objectListeners)) {
            try {
                listener.onClosed(object);
            } catch (final RuntimeException ignored) {
                incrementInternal(MetricNames.METRIC_OBJECT_ADAPTER_FAILURES);
            }
        }
    }

    private void rejectUpdate() {
        incrementInternal(MetricNames.METRIC_UPDATES_REJECTED);
    }

    private void rejectObjectOpen() {
        incrementInternal(MetricNames.METRIC_OBJECTS_REJECTED);
    }

    private void incrementInternal(final String name) {
        final LongAdder counter = counters.get(name);
        if (counter != null) {
            counter.increment();
        }
    }

    private void setInternal(final String name, final long value) {
        final AtomicLong gauge = gauges.get(name);
        if (gauge != null) {
            gauge.set(value);
        }
    }

    private static void populateStores(final MetricCatalog metricCatalog,
                                       final Map<String, LongAdder> newCounters,
                                       final Map<String, AtomicLong> newGauges) {
        for (final MetricDescriptor descriptor : metricCatalog.descriptors()) {
            if (descriptor.kind() == MetricKind.COUNTER) {
                newCounters.put(descriptor.name(), new LongAdder());
            } else {
                newGauges.put(descriptor.name(), new AtomicLong());
            }
        }
    }

    private static boolean increment(final Map<String, LongAdder> targetCounters,
                                     final String name,
                                     final long delta) {
        if (name == null || delta < 0) {
            return false;
        }
        final LongAdder counter = targetCounters.get(name);
        if (counter == null) {
            return false;
        }
        counter.add(delta);
        return true;
    }

    private static boolean set(final Map<String, AtomicLong> targetGauges,
                               final String name,
                               final long value) {
        if (name == null) {
            return false;
        }
        final AtomicLong gauge = targetGauges.get(name);
        if (gauge == null) {
            return false;
        }
        gauge.set(value);
        return true;
    }

    private static long currentValue(final Map<String, LongAdder> currentCounters,
                                     final Map<String, AtomicLong> currentGauges,
                                     final String name) {
        final LongAdder counter = currentCounters.get(name);
        if (counter != null) {
            return counter.sum();
        }
        final AtomicLong gauge = currentGauges.get(name);
        if (gauge != null) {
            return gauge.get();
        }
        throw new IllegalArgumentException("Unknown metric name");
    }

    private MetricSnapshot snapshot(final MetricCatalog metricCatalog,
                                    final Map<String, LongAdder> currentCounters,
                                    final Map<String, AtomicLong> currentGauges,
                                    final Instant objectStartedAt) {
        final Map<String, Long> values = new LinkedHashMap<>();
        for (final MetricDescriptor descriptor : metricCatalog.descriptors()) {
            values.put(descriptor.name(),
                       currentValue(currentCounters, currentGauges, descriptor.name()));
        }
        return new MetricSnapshot(objectStartedAt, clock.instant(), values);
    }

    private record MetricObjectKey(String typeName, long instanceNumber) {
    }

    private final class LiveMetricObject implements MetricObject {
        private final MetricObjectType type;
        private final long instanceNumber;
        private final Instant objectStartedAt;
        private final Map<String, LongAdder> objectCounters;
        private final Map<String, AtomicLong> objectGauges;
        private boolean closed;

        private LiveMetricObject(final MetricObjectType type,
                                 final long instanceNumber,
                                 final Instant objectStartedAt) {
            this.type = type;
            this.instanceNumber = instanceNumber;
            this.objectStartedAt = objectStartedAt;
            final Map<String, LongAdder> newCounters = new LinkedHashMap<>();
            final Map<String, AtomicLong> newGauges = new LinkedHashMap<>();
            populateStores(type.metrics(), newCounters, newGauges);
            objectCounters = Map.copyOf(newCounters);
            objectGauges = Map.copyOf(newGauges);
        }

        @Override
        public MetricObjectType type() {
            return type;
        }

        @Override
        public long instanceNumber() {
            return instanceNumber;
        }

        @Override
        public synchronized boolean isClosed() {
            return closed;
        }

        @Override
        public void incrementCounter(final String name) {
            incrementCounter(name, 1);
        }

        @Override
        public synchronized void incrementCounter(final String name, final long delta) {
            if (closed || !increment(objectCounters, name, delta)) {
                rejectUpdate();
            }
        }

        @Override
        public synchronized void setGauge(final String name, final long value) {
            if (closed || !set(objectGauges, name, value)) {
                rejectUpdate();
            }
        }

        @Override
        public synchronized long value(final String name) {
            return currentValue(objectCounters, objectGauges, name);
        }

        @Override
        public synchronized MetricSnapshot snapshot() {
            return MetricRegistry.this.snapshot(
                    type.metrics(), objectCounters, objectGauges, objectStartedAt);
        }

        @Override
        public void close() {
            closeObject(this);
        }

        private synchronized boolean markClosed() {
            if (closed) {
                return false;
            }
            closed = true;
            return true;
        }

        private MetricObjectKey key() {
            return new MetricObjectKey(type.name(), instanceNumber);
        }
    }
}
