package rsp.telemetry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable registry assembled from explicitly typed telemetry bindings. */
public final class MapTelemetryRegistry implements TelemetryRegistry {
    private final Map<TelemetryKey<?>, Telemetry<?>> scalarSources;
    private final Map<TelemetryKey<?>, TelemetrySeries<?>> seriesSources;

    private MapTelemetryRegistry(Map<TelemetryKey<?>, Telemetry<?>> scalarSources,
                                 Map<TelemetryKey<?>, TelemetrySeries<?>> seriesSources) {
        this.scalarSources = Map.copyOf(scalarSources);
        this.seriesSources = Map.copyOf(seriesSources);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Telemetry<T> resolve(TelemetryKey<T> key) {
        Objects.requireNonNull(key, "key");
        Telemetry<?> source = scalarSources.get(key);
        if (source == null) {
            throw new IllegalArgumentException("No scalar telemetry registered for " + key.id());
        }
        return (Telemetry<T>) source;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> TelemetrySeries<T> resolveSeries(TelemetryKey<T> key) {
        Objects.requireNonNull(key, "key");
        TelemetrySeries<?> source = seriesSources.get(key);
        if (source == null) {
            throw new IllegalArgumentException("No telemetry series registered for " + key.id());
        }
        return (TelemetrySeries<T>) source;
    }

    public static final class Builder {
        private final Map<TelemetryKey<?>, Telemetry<?>> scalarSources = new LinkedHashMap<>();
        private final Map<TelemetryKey<?>, TelemetrySeries<?>> seriesSources = new LinkedHashMap<>();

        public <T> Builder bind(TelemetryKey<T> key, Telemetry<T> source) {
            putUnique(scalarSources, key, source, "scalar");
            return this;
        }

        public <T> Builder bindSeries(TelemetryKey<T> key, TelemetrySeries<T> source) {
            putUnique(seriesSources, key, source, "series");
            return this;
        }

        public MapTelemetryRegistry build() {
            return new MapTelemetryRegistry(scalarSources, seriesSources);
        }

        private static <S> void putUnique(Map<TelemetryKey<?>, S> target,
                                          TelemetryKey<?> key,
                                          S source,
                                          String kind) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(source, "source");
            if (target.putIfAbsent(key, source) != null) {
                throw new IllegalArgumentException(
                        "Duplicate " + kind + " telemetry binding for " + key.id());
            }
        }
    }
}
