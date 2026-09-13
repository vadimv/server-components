package rsp.metrics;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable metadata for one bounded-cardinality framework metric.
 *
 * @param name transport-neutral metric name
 * @param kind update semantics
 * @param unit UCUM-style unit, with {@code 1} for dimensionless values
 * @param description operator-facing description that must not contain runtime data
 * @param jmxAttribute stable JMX attribute name
 */
public record MetricDescriptor(String name,
                               MetricKind kind,
                               String unit,
                               String description,
                               String jmxAttribute) {
    private static final Pattern METRIC_NAME =
            Pattern.compile("[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9_]*)+");
    private static final Pattern JMX_ATTRIBUTE = Pattern.compile("[A-Z][A-Za-z0-9]*");

    /** Validates and creates a metric descriptor. */
    public MetricDescriptor {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(jmxAttribute, "jmxAttribute");
        if (!METRIC_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid metric name");
        }
        if (unit.isBlank()) {
            throw new IllegalArgumentException("Metric unit must not be blank");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("Metric description must not be blank");
        }
        if (!JMX_ATTRIBUTE.matcher(jmxAttribute).matches()) {
            throw new IllegalArgumentException("Invalid JMX attribute name");
        }
    }

    /** Creates counter metadata. */
    public static MetricDescriptor counter(final String name,
                                           final String unit,
                                           final String description,
                                           final String jmxAttribute) {
        return new MetricDescriptor(name, MetricKind.COUNTER, unit, description, jmxAttribute);
    }

    /** Creates gauge metadata. */
    public static MetricDescriptor gauge(final String name,
                                         final String unit,
                                         final String description,
                                         final String jmxAttribute) {
        return new MetricDescriptor(name, MetricKind.GAUGE, unit, description, jmxAttribute);
    }
}
