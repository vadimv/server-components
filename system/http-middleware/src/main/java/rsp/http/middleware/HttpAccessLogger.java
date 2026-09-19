package rsp.http.middleware;

import java.util.Objects;

/** Sink for one completed HTTP access event. */
@FunctionalInterface
public interface HttpAccessLogger {
    void log(HttpAccessEvent event);

    /** Writes a query-free, message-free access line through {@link System.Logger}. */
    static HttpAccessLogger system(System.Logger logger) {
        Objects.requireNonNull(logger, "logger");
        return event -> logger.log(System.Logger.Level.INFO, () -> {
            String requestId = event.requestId().map(value -> ", request_id=" + value).orElse("");
            String failure = event.failureType().map(value -> ", failure=" + value).orElse("");
            return "HTTP request completed [method=" + event.method()
                    + ", path=" + event.rawPath()
                    + ", status=" + event.statusCode()
                    + ", duration_ms=" + event.duration().toMillis()
                    + requestId + failure + "]";
        });
    }
}
