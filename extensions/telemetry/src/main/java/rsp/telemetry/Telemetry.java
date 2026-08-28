package rsp.telemetry;

import java.util.Optional;
import java.util.function.Consumer;

/** A scalar, push-capable telemetry source. */
public interface Telemetry<T> {

    Optional<TelemetrySample<T>> snapshot();

    Subscription subscribe(Consumer<TelemetrySample<T>> subscriber);
}
