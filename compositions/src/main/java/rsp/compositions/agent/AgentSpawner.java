package rsp.compositions.agent;

import rsp.component.Lookup;

/**
 * Centralized entrypoint for agent session creation.
 * <p>
 * Implementations handle ABAC {@code canSpawn} evaluation, approval workflows,
 * and delegation grant minting. Execution remains governed by {@link IntentGate}
 * and discovery by {@link AgentActionFilter} via {@link AgentContext}.
 */
public interface AgentSpawner {
    SpawnResult spawn(SpawnRequest request, Lookup lookup);
}
