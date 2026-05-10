package rsp.compositions.agent;

/**
 * UI sink for runtime status updates. The runtime emits messages
 * and the embedding contract decides how to render them.
 * <p>
 * Phase 1A keeps the contract intentionally minimal: callers pass
 * already-formatted strings. Future phases may introduce semantic
 * events (replies vs progress vs blocks) when richer separation
 * across surfaces is required.
 */
public interface AgentFeedback {

    /** Emit a new status message. */
    void send(String message);

    /** Replace the most recently emitted status message with this one. */
    void updateLast(String message);
}
