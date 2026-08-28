package rsp.telemetry;

import java.util.List;
import java.util.function.Consumer;

/** A push-capable telemetry source whose snapshots contain an ordered series. */
public interface TelemetrySeries<T> {

    List<TelemetrySample<T>> snapshot();

    Subscription subscribe(Consumer<List<TelemetrySample<T>>> subscriber);
}
