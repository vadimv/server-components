package rsp.app.posts.services;

import java.time.Instant;
import java.util.Objects;

public record LogEntry(long sequence, Instant timestamp, Level level, String message) {

    public LogEntry {
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        level = Objects.requireNonNull(level, "level");
        message = message == null ? "" : message;
    }

    public enum Level {
        INFO, WARN, ERROR
    }
}
