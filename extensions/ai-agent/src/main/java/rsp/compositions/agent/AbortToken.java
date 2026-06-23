package rsp.compositions.agent;

/**
 * Cooperative cancellation signal for in-flight agent work.
 * <p>
 * Callers create a token, pass it into the work (LLM call, loop iteration),
 * and call {@link #cancel()} to request termination. The work checks
 * {@link #isCancelled()} at convenient boundaries.
 * <p>
 * Cancellation is one-way and idempotent.
 */
public final class AbortToken {

    private volatile boolean cancelled;

    /** Request cancellation. Safe to call from any thread. Idempotent. */
    public void cancel() {
        this.cancelled = true;
    }

    /** @return {@code true} if {@link #cancel()} has been called. */
    public boolean isCancelled() {
        return cancelled;
    }
}
