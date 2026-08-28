package rsp.compositions.block;

import rsp.compositions.schema.DataSchema;

import java.util.Map;
import java.util.Objects;

/**
 * Structured metadata a block exposes for external consumers (AI agents, tools, debuggers).
 * <p>
 * Generic by design: not tied to CRUD or any specific block type.
 * Each block fills in whatever is relevant:
 * <ul>
 *   <li>List block: {@code state = Map.of("page", 1, "items", [...])}</li>
 *   <li>Edit block: {@code state = Map.of("entity", Map.of("id", "2", ...))}</li>
 *   <li>Game block: {@code state = Map.of("turn", "white", "board", [...])}</li>
 * </ul>
 *
 * @param title       short label, e.g. "Posts", "Chess"
 * @param description what this block does and how to interpret its state
 * @param schema      field definitions (nullable — not all blocks have a schema)
 * @param state       current data the consumer should see
 */
public record BlockMetadata(
    String title,
    String description,
    DataSchema schema,
    Map<String, Object> state
) {
    public BlockMetadata {
        Objects.requireNonNull(title);
        state = state != null ? Map.copyOf(state) : Map.of();
    }
}
