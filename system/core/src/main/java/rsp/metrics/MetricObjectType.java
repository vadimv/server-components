package rsp.metrics;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Fixed, transport-neutral schema for one kind of lifecycle-bound metric object.
 *
 * @param name stable semantic type name
 * @param jmxType stable JMX {@code type} property
 * @param metrics fixed metrics exposed by every live instance
 */
public record MetricObjectType(String name,
                               String jmxType,
                               MetricCatalog metrics) {
    private static final Pattern TYPE_NAME =
            Pattern.compile("[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9_]*)+");
    private static final Pattern JMX_TYPE = Pattern.compile("[A-Z][A-Za-z0-9]*");

    /** Validates a metric-object type. */
    public MetricObjectType {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(jmxType, "jmxType");
        Objects.requireNonNull(metrics, "metrics");
        if (!TYPE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid metric-object type name");
        }
        if (!JMX_TYPE.matcher(jmxType).matches()) {
            throw new IllegalArgumentException("Invalid metric-object JMX type");
        }
        if ("Framework".equals(jmxType)) {
            throw new IllegalArgumentException("Metric-object JMX type is reserved");
        }
    }
}
