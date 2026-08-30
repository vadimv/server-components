package rsp.compositions.block;

/**
 * Actions and row identity exposed by a list block to its view.
 *
 * @param rowKey schema field containing a stable, unique row identifier
 * @param canCreate whether create controls and intents are available
 * @param canEdit whether edit controls and intents are available
 * @param canDelete whether row and bulk delete controls and intents are available
 */
public record ListCapabilities(String rowKey,
                               boolean canCreate,
                               boolean canEdit,
                               boolean canDelete) {
    public ListCapabilities {
        if (rowKey == null || rowKey.isBlank()) {
            throw new IllegalArgumentException("rowKey is required");
        }
        rowKey = rowKey.trim();
    }

    /** Standard capabilities for an ID-keyed CRUD list. */
    public static ListCapabilities crud() {
        return new ListCapabilities("id", true, true, true);
    }
}
