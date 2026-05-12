package rsp.compositions.agent;

import rsp.compositions.agent.AgentService.AgentResult;

/**
 * Pluggable continuation decision for the agent loop.
 * <p>
 * Called by {@link AgentRuntime} after each successful dispatch to decide
 * whether to invoke the LLM again with the same goal.
 * <p>
 * Result-based termination (e.g. {@code TextReply}, {@code PlanResult},
 * dispatch failure, approval-required) is enforced by the runtime
 * independently of this policy — implementations only need to handle
 * step-count / budget / external-signal concerns.
 */
public interface LoopPolicy {

    /**
     * @param lastResult the dispatched result of the just-completed step
     * @param stepCount  the number of completed steps so far (starting at 1)
     * @return {@code true} to run another iteration, {@code false} to stop
     */
    boolean shouldContinue(AgentResult lastResult, int stepCount);

    /**
     * Bounded continuation: stop once {@code stepCount} reaches {@code maxSteps}.
     */
    static LoopPolicy budget(int maxSteps) {
        if (maxSteps < 1) {
            throw new IllegalArgumentException("maxSteps must be >= 1");
        }
        return (result, stepCount) -> stepCount < maxSteps;
    }

    /** Default policy: budget of 8 steps. */
    LoopPolicy DEFAULT = budget(8);
}
