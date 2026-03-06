package rsp.compositions.agent;

/**
 * Opt-in interface for agent-discoverable contracts.
 * <p>
 * Contracts that implement this interface provide a natural-language
 * description of their capabilities and current state, enabling
 * an AI agent to discover and interact with them.
 * <p>
 * Discovery: {@code if (contract instanceof AgentInfo info) { info.agentDescription(); }}
 */
public interface AgentInfo {
    /**
     * Returns a natural-language description of this contract's capabilities and current state.
     * <p>
     * The description should include:
     * <ul>
     *   <li>What the contract does (e.g., "Displays a list of Posts")</li>
     *   <li>Current state (e.g., "Current page: 2, sort: asc")</li>
     *   <li>Supported actions (e.g., "Supports: page navigation, selection, create, edit")</li>
     * </ul>
     *
     * @return human-readable description for agent consumption
     */
    String agentDescription();
}
