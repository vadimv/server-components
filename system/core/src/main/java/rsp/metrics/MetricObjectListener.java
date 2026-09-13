package rsp.metrics;

/**
 * Adapter SPI for lifecycle-bound metric objects. Implementations should finish
 * callbacks quickly and must not derive metadata from application runtime data.
 */
public interface MetricObjectListener {

    /** Called after an object becomes live. */
    void onOpened(MetricObject object);

    /** Called after an object is closed and removed from active snapshots. */
    void onClosed(MetricObject object);

    /** Idempotent listener-registration handle. */
    interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}
