package rsp.compositions.agent;

import rsp.compositions.schema.DataSchema;

import java.util.Map;
import java.util.Objects;

/**
 * Structured metadata a contract exposes for external consumers (AI agents, tools, debuggers).
 * <p>
 * Generic by design: not tied to CRUD or any specific contract type.
 * Each contract fills in whatever is relevant:
 * <ul>
 *   <li>List contract: {@code state = Map.of("page", 1, "items", [...])}</li>
 *   <li>Edit contract: {@code state = Map.of("entity", Map.of("id", "2", ...))}</li>
 *   <li>Game contract: {@code state = Map.of("turn", "white", "board", [...])}</li>
 * </ul>
 *
 * @param title       short label, e.g. "Posts", "Chess"
 * @param description what this contract does and how to interpret its state
 * @param schema      field definitions (nullable — not all contracts have a schema)
 * @param state       current data the consumer should see
 */
public record ContractMetadata(
    String title,
    String description,
    DataSchema schema,
    Map<String, Object> state
) {
    public ContractMetadata {
        Objects.requireNonNull(title);
        state = state != null ? Map.copyOf(state) : Map.of();
    }
}
