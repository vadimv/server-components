package rsp.compositions.agent;

/**
 * Result of an {@link AgentSpawner#spawn} request.
 */
public sealed interface SpawnResult {
    record Approved(AgentSession session) implements SpawnResult {}
    record Denied(String reason) implements SpawnResult {}
    record RequiresApproval(String ticketId, String reason) implements SpawnResult {}
}
