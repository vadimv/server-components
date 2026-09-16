package rsp.metrics;

/**
 * One lifecycle-bound set of metrics, such as a mounted component or live
 * connection. Instances contain numeric values only and must be closed by their
 * runtime owner.
 */
public interface MetricObject extends Metrics, AutoCloseable {

    /** Returns the fixed schema shared by all objects of this type. */
    MetricObjectType type();

    /** Returns the opaque, process-local ordinal allocated within this object's type. */
    long instanceNumber();

    /** Returns whether this object was closed or represents disabled instrumentation. */
    boolean isClosed();

    /** Returns one current value, failing if the metric is outside this object's schema. */
    long value(String name);

    /** Copies the object's current values and timestamps. */
    MetricSnapshot snapshot();

    /** Closes this object and removes it from every attached adapter. Idempotent. */
    @Override
    void close();

    /** Creates a disabled handle for an unavailable or rejected object type. */
    static MetricObject noop(final MetricObjectType type) {
        return new NoOpMetricObject(type);
    }
}
