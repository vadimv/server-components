package rsp.application;

/** Process-level lifecycle which may be safely stopped more than once. */
public interface ApplicationLifecycle extends AutoCloseable {
    /** Starts this application resource. The default resource needs no startup. */
    default void start() {
    }

    /** Stops this application resource. Implementations must make this idempotent. */
    default void stop() {
    }

    @Override
    default void close() {
        stop();
    }
}
