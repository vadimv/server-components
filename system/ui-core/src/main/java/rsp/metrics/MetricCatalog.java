package rsp.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable allow-list of metrics exposed by a registry and its adapters. */
public final class MetricCatalog {
    private final List<MetricDescriptor> descriptors;
    private final Map<String, MetricDescriptor> byName;
    private final Map<String, MetricDescriptor> byJmxAttribute;

    private MetricCatalog(final List<MetricDescriptor> descriptors) {
        if (descriptors.isEmpty()) {
            throw new IllegalArgumentException("Metric catalog must not be empty");
        }
        final Map<String, MetricDescriptor> names = new LinkedHashMap<>();
        final Map<String, MetricDescriptor> jmxAttributes = new LinkedHashMap<>();
        for (final MetricDescriptor descriptor : descriptors) {
            Objects.requireNonNull(descriptor, "descriptor");
            if (names.putIfAbsent(descriptor.name(), descriptor) != null) {
                throw new IllegalArgumentException("Duplicate metric name: " + descriptor.name());
            }
            if (jmxAttributes.putIfAbsent(descriptor.jmxAttribute(), descriptor) != null) {
                throw new IllegalArgumentException("Duplicate JMX attribute: " + descriptor.jmxAttribute());
            }
        }
        this.descriptors = List.copyOf(descriptors);
        this.byName = Map.copyOf(names);
        this.byJmxAttribute = Map.copyOf(jmxAttributes);
    }

    /** Creates a catalog in the supplied stable iteration order. */
    public static MetricCatalog of(final MetricDescriptor... descriptors) {
        Objects.requireNonNull(descriptors, "descriptors");
        return new MetricCatalog(List.of(descriptors));
    }

    /**
     * Returns a new catalog with explicitly approved application metrics appended.
     * Duplicate names and JMX attributes are rejected.
     */
    public MetricCatalog with(final MetricDescriptor... additionalDescriptors) {
        Objects.requireNonNull(additionalDescriptors, "additionalDescriptors");
        final List<MetricDescriptor> combined = new ArrayList<>(descriptors);
        Collections.addAll(combined, additionalDescriptors);
        return new MetricCatalog(combined);
    }

    /** Returns all descriptors in stable catalog order. */
    public List<MetricDescriptor> descriptors() {
        return descriptors;
    }

    /** Looks up metadata by its transport-neutral name. */
    public Optional<MetricDescriptor> descriptor(final String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /** Looks up metadata by its JMX attribute name. */
    public Optional<MetricDescriptor> descriptorForJmxAttribute(final String attribute) {
        return Optional.ofNullable(byJmxAttribute.get(attribute));
    }

    @Override
    public boolean equals(final Object other) {
        return this == other
                || other instanceof MetricCatalog catalog
                && descriptors.equals(catalog.descriptors);
    }

    @Override
    public int hashCode() {
        return descriptors.hashCode();
    }

    @Override
    public String toString() {
        return "MetricCatalog" + descriptors;
    }
}
