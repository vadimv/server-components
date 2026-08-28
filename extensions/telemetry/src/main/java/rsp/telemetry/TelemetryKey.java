package rsp.telemetry;

import java.util.Objects;

/** A stable, typed name for a telemetry signal. */
public record TelemetryKey<T>(String id, Class<T> valueType, String unit) {

    public TelemetryKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(valueType, "valueType");
        unit = unit == null ? "" : unit;
        if (id.isBlank()) {
            throw new IllegalArgumentException("Telemetry id must not be blank");
        }
    }

    public TelemetryKey(String id, Class<T> valueType) {
        this(id, valueType, "");
    }

    public static <T> TelemetryKey<T> of(String id, Class<T> valueType) {
        return new TelemetryKey<>(id, valueType);
    }

    public static <T> TelemetryKey<T> of(String id, Class<T> valueType, String unit) {
        return new TelemetryKey<>(id, valueType, unit);
    }
}
