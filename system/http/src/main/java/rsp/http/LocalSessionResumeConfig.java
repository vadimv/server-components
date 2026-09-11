package rsp.http;

import java.time.Duration;
import java.util.Objects;

/**
 * Bounds for retaining a live page while its browser WebSocket is detached.
 *
 * @param gracePeriod how long a detached page remains resumable
 * @param maxBufferedMessages maximum number of unacknowledged server messages retained per page
 * @param maxBufferedBytes maximum encoded size of unacknowledged server messages retained per page
 */
public record LocalSessionResumeConfig(Duration gracePeriod,
                                       int maxBufferedMessages,
                                       long maxBufferedBytes) {
    /** Default time a detached page remains available for a same-process resume. */
    public static final Duration DEFAULT_GRACE_PERIOD = Duration.ofSeconds(60);
    /** Default maximum number of unacknowledged server messages retained per page. */
    public static final int DEFAULT_MAX_BUFFERED_MESSAGES = 4_096;
    /** Default maximum encoded size of unacknowledged server messages retained per page. */
    public static final long DEFAULT_MAX_BUFFERED_BYTES = 4L * 1024L * 1024L;

    public LocalSessionResumeConfig {
        Objects.requireNonNull(gracePeriod, "gracePeriod");
        if (gracePeriod.isZero() || gracePeriod.isNegative()) {
            throw new IllegalArgumentException("gracePeriod must be greater than zero");
        }
        if (maxBufferedMessages < 1) {
            throw new IllegalArgumentException("maxBufferedMessages must be greater than zero");
        }
        if (maxBufferedBytes < 1) {
            throw new IllegalArgumentException("maxBufferedBytes must be greater than zero");
        }
    }

    /**
     * Returns the recommended bounded local-resume configuration.
     */
    public static LocalSessionResumeConfig defaults() {
        return new LocalSessionResumeConfig(DEFAULT_GRACE_PERIOD,
                                            DEFAULT_MAX_BUFFERED_MESSAGES,
                                            DEFAULT_MAX_BUFFERED_BYTES);
    }
}
