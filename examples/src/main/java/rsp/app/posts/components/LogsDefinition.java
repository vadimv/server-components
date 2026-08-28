package rsp.app.posts.components;

import rsp.app.posts.services.LogEntry;
import rsp.compositions.dashboard.WidgetDefinition;
import rsp.telemetry.TelemetryKey;

import java.util.Map;
import java.util.Objects;

/** Immutable app-specific log widget declaration. */
public record LogsDefinition(String id,
                             String title,
                             String description,
                             TelemetryKey<LogEntry> series) implements WidgetDefinition {
    public LogsDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        description = description == null ? "" : description;
        Objects.requireNonNull(series, "series");
        if (id.isBlank() || title.isBlank()) {
            throw new IllegalArgumentException("Log widget id and title must not be blank");
        }
    }

    @Override
    public String kind() {
        return "log-stream";
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("series", series.id());
    }
}
