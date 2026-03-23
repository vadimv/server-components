package rsp.compositions.agent;

import rsp.component.Lookup;

import java.util.List;

/**
 * Controls which actions an agent can discover.
 * <p>
 * This is the discovery-time complement to {@link ActionGate} (execution-time):
 * <ul>
 *   <li>{@code AgentActionFilter} — "what actions does this agent see?" (before prompt parsing)</li>
 *   <li>{@code ActionGate} — "is this specific action allowed?" (after prompt parsing)</li>
 * </ul>
 * <p>
 * Applied inside {@link AgentContext} — the agent receives pre-filtered actions,
 * never seeing what its scope excludes.
 */
public interface AgentActionFilter {
    /**
     * Filter the list of actions visible to the agent.
     *
     * @param actions the full list of declared actions from the contract
     * @param context the current context (for role checks, user attributes, etc.)
     * @return the filtered list of actions the agent should see
     */
    List<AgentAction> filter(List<AgentAction> actions, Lookup context);
}
