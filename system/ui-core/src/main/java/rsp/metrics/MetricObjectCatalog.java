package rsp.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable allow-list of lifecycle-bound metric-object types. */
public final class MetricObjectCatalog {
    private static final MetricObjectCatalog EMPTY = new MetricObjectCatalog(List.of());

    private final List<MetricObjectType> types;
    private final Map<String, MetricObjectType> byName;

    private MetricObjectCatalog(final List<MetricObjectType> types) {
        final Map<String, MetricObjectType> names = new LinkedHashMap<>();
        final Map<String, MetricObjectType> jmxTypes = new LinkedHashMap<>();
        final Map<String, MetricObjectType> metricNames = new LinkedHashMap<>();
        for (final MetricObjectType type : types) {
            Objects.requireNonNull(type, "type");
            if (names.putIfAbsent(type.name(), type) != null) {
                throw new IllegalArgumentException("Duplicate metric-object type name: " + type.name());
            }
            if (jmxTypes.putIfAbsent(type.jmxType(), type) != null) {
                throw new IllegalArgumentException("Duplicate metric-object JMX type: " + type.jmxType());
            }
            for (final MetricDescriptor metric : type.metrics().descriptors()) {
                final MetricObjectType previous = metricNames.putIfAbsent(metric.name(), type);
                if (previous != null) {
                    throw new IllegalArgumentException("Metric name used by multiple object types: " + metric.name());
                }
            }
        }
        this.types = List.copyOf(types);
        this.byName = Map.copyOf(names);
    }

    /** Returns a catalog with no permitted metric-object types. */
    public static MetricObjectCatalog empty() {
        return EMPTY;
    }

    /** Creates a catalog in stable type order. */
    public static MetricObjectCatalog of(final MetricObjectType... types) {
        Objects.requireNonNull(types, "types");
        return types.length == 0 ? EMPTY : new MetricObjectCatalog(List.of(types));
    }

    /**
     * Returns a new catalog with explicitly approved object types appended.
     * Duplicate semantic names, JMX types, and metric names are rejected.
     */
    public MetricObjectCatalog with(final MetricObjectType... additionalTypes) {
        Objects.requireNonNull(additionalTypes, "additionalTypes");
        final List<MetricObjectType> combined = new ArrayList<>(types);
        Collections.addAll(combined, additionalTypes);
        return new MetricObjectCatalog(combined);
    }

    /** Returns all allowed object types in stable catalog order. */
    public List<MetricObjectType> types() {
        return types;
    }

    /** Looks up a type by its stable semantic name. */
    public Optional<MetricObjectType> type(final String name) {
        return Optional.ofNullable(byName.get(name));
    }

    @Override
    public boolean equals(final Object other) {
        return this == other
                || other instanceof MetricObjectCatalog catalog
                && types.equals(catalog.types);
    }

    @Override
    public int hashCode() {
        return types.hashCode();
    }

    @Override
    public String toString() {
        return "MetricObjectCatalog" + types;
    }
}
