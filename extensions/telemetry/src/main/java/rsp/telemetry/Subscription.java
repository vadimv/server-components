package rsp.telemetry;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** A closeable telemetry registration. Implementations must be idempotent. */
@FunctionalInterface
public interface Subscription extends AutoCloseable {

    @Override
    void close();

    static Subscription none() {
        return () -> { };
    }

    static Subscription once(Runnable closeAction) {
        Objects.requireNonNull(closeAction, "closeAction");
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true)) {
                closeAction.run();
            }
        };
    }
}
