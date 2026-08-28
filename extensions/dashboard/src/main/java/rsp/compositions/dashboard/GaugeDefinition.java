package rsp.compositions.dashboard;

import rsp.telemetry.TelemetryKey;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** A bounded numeric gauge widget. */
public record GaugeDefinition(String id,
                              String title,
                              String description,
                              TelemetryKey<Double> value,
                              double minimum,
                              double maximum,
                              int decimals,
                              Duration staleAfter) implements WidgetDefinition {
    public GaugeDefinition {
        DashboardDefinition.requireText(id, "Widget id");
        DashboardDefinition.requireText(title, "Widget title");
        description = description == null ? "" : description;
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(staleAfter, "staleAfter");
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum >= maximum) {
            throw new IllegalArgumentException("Gauge range must contain finite minimum < maximum");
        }
        if (decimals < 0 || decimals > 12) {
            throw new IllegalArgumentException("decimals must be between 0 and 12");
        }
        if (staleAfter.isNegative()) {
            throw new IllegalArgumentException("staleAfter must not be negative");
        }
    }

    @Override
    public String kind() {
        return "gauge";
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("signal", value.id(), "unit", value.unit(), "minimum", minimum,
                "maximum", maximum, "decimals", decimals, "staleAfterMs", staleAfter.toMillis());
    }
}
