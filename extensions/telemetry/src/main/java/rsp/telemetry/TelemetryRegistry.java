package rsp.telemetry;

/** Resolves typed signal names without exposing transport details to UI code. */
public interface TelemetryRegistry {

    <T> Telemetry<T> resolve(TelemetryKey<T> key);

    <T> TelemetrySeries<T> resolveSeries(TelemetryKey<T> key);
}
