package rsp.compositions.agent;

import rsp.component.EventKey;

/**
 * Pluggable decision for whether an event should stop an in-flight agent loop.
 * <p>
 * The runtime calls {@link #shouldStop(EventOrigin, EventKey)} from its
 * {@link AgentRuntime#notifyEvent} entry point. Returning {@code true}
 * causes the runtime to cancel its current {@link AbortToken}, which the
 * loop observes at the next iteration boundary.
 * <p>
 * Default implementations:
 * <ul>
 *   <li>{@link #strictStop()} — any {@code USER}-origin event stops the loop.
 *       This is the safe default for HITL workflows: the user always
 *       overrides an in-progress agent.</li>
 *   <li>{@link #never()} — never stops. Useful for fully-autonomous test
 *       scenarios or when interruption is handled externally.</li>
 * </ul>
 * <p>
 * Custom implementations can be more nuanced — e.g. stop on navigation but
 * not on form-field changes — by inspecting the {@code key}.
 */
public interface InterruptionPolicy {

    /**
     * @param origin where the event came from
     * @param key    the event key (nullable — some callers pass {@code null}
     *               for synthetic "the user did something" signals)
     * @return {@code true} to interrupt the running agent loop
     */
    boolean shouldStop(EventOrigin origin, EventKey<?> key);

    /** Strict policy: any USER-origin event stops the loop. Default for HITL. */
    static InterruptionPolicy strictStop() {
        return (origin, key) -> origin == EventOrigin.USER;
    }

    /** Never-stop policy: ignore everything. Useful in tests / autonomous mode. */
    static InterruptionPolicy never() {
        return (origin, key) -> false;
    }
}
