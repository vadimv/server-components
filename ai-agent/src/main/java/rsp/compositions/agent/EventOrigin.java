package rsp.compositions.agent;

/**
 * Source of an event from the runtime's perspective.
 * <p>
 * Used by {@link InterruptionPolicy} to decide whether an event should
 * stop the running agent loop. The agent's own dispatches are
 * {@link #AGENT}; everything else (user DOM interactions, framework
 * timers, etc.) is {@link #USER}.
 */
public enum EventOrigin {
    /** The agent runtime itself produced this event (via ActionDispatcher). */
    AGENT,
    /** The event came from outside the agent — typically a user interaction. */
    USER
}
