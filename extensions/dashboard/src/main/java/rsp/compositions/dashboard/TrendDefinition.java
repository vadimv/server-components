package rsp.compositions.dashboard;

import rsp.telemetry.TelemetryKey;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** A time-windowed numeric telemetry series widget. */
public record TrendDefinition(String id,
                              String title,
                              String description,
                              TelemetryKey<Double> series,
                              Duration window,
                              int decimals) implements WidgetDefinition {
    public TrendDefinition {
        DashboardDefinition.requireText(id, "Widget id");
        DashboardDefinition.requireText(title, "Widget title");
        description = description == null ? "" : description;
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(window, "window");
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Trend window must be positive");
        }
        if (decimals < 0 || decimals > 12) {
            throw new IllegalArgumentException("decimals must be between 0 and 12");
        }
    }

    @Override
    public String kind() {
        return "trend";
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("series", series.id(), "unit", series.unit(), "windowMs", window.toMillis(),
                "decimals", decimals);
    }
}
