package rsp.compositions.dashboard;

import rsp.telemetry.TelemetryKey;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** A current numeric value widget. */
public record ValueDefinition(String id,
                              String title,
                              String description,
                              TelemetryKey<Double> value,
                              int decimals,
                              Duration staleAfter) implements WidgetDefinition {
    public ValueDefinition {
        DashboardDefinition.requireText(id, "Widget id");
        DashboardDefinition.requireText(title, "Widget title");
        description = description == null ? "" : description;
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(staleAfter, "staleAfter");
        if (decimals < 0 || decimals > 12) {
            throw new IllegalArgumentException("decimals must be between 0 and 12");
        }
        if (staleAfter.isNegative()) {
            throw new IllegalArgumentException("staleAfter must not be negative");
        }
    }

    @Override
    public String kind() {
        return "value";
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("signal", value.id(), "unit", value.unit(), "decimals", decimals,
                "staleAfterMs", staleAfter.toMillis());
    }
}
