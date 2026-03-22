package rsp.compositions.agent;

/**
 * Stores user approval/denial decisions for agent delegation requests.
 * <p>
 * Keyed by session ID. The store holds only the user's intent (approve/deny).
 */
public interface DelegationStore {

    record Decision(boolean approved, String sessionKey) {}

    /**
     * Record a user's approval or denial decision.
     */
    void recordDecision(String sessionKey, boolean approved);

    /**
     * Look up the stored decision for the given session.
     *
     * @return the Decision, or {@code null} if no decision has been recorded
     */
    Decision getDecision(String sessionKey);

    /**
     * Remove the decision for the given session (cleanup on session end).
     */
    void removeDecision(String sessionKey);
}
