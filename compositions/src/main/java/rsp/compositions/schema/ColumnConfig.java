package rsp.compositions.schema;

import java.util.function.Function;

/**
 * Configuration for how a field is displayed as a column in list views.
 * <p>
 * ColumnConfig defines list-specific display properties like sortability,
 * filterability, width, and alignment. It references a field by name
 * rather than redefining the data.
 * <p>
 * Example:
 * <pre>{@code
 * DataSchema schema = DataSchema.builder()
 *     .field("title", FieldType.STRING).required()
 *     .column("title").sortable().filterable().width("40%")
 *     .build();
 * }</pre>
 *
 * @param fieldName The name of the field this column displays
 * @param sortable Whether the column can be sorted
 * @param filterable Whether the column can be filtered
 * @param width CSS width for the column (e.g., "200px", "30%")
 * @param align Text alignment for column content
 * @param formatter Optional function to format cell values for display
 */
public record ColumnConfig(
    String fieldName,
    boolean sortable,
    boolean filterable,
    String width,
    TextAlign align,
    Function<Object, String> formatter
) {
    /**
     * Canonical constructor with validation and defaults.
     */
    public ColumnConfig {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName is required");
        }
        align = align != null ? align : TextAlign.LEFT;
    }

    /**
     * Create a minimal column config with defaults.
     *
     * @param fieldName The field name
     */
    public ColumnConfig(String fieldName) {
        this(fieldName, false, false, null, TextAlign.LEFT, null);
    }

    // ========== Immutable Update Methods ==========

    /**
     * Return a new ColumnConfig with sortable enabled.
     */
    public ColumnConfig withSortable() {
        return new ColumnConfig(fieldName, true, filterable, width, align, formatter);
    }

    /**
     * Return a new ColumnConfig with filterable enabled.
     */
    public ColumnConfig withFilterable() {
        return new ColumnConfig(fieldName, sortable, true, width, align, formatter);
    }

    /**
     * Return a new ColumnConfig with the specified width.
     *
     * @param w CSS width (e.g., "200px", "30%")
     */
    public ColumnConfig withWidth(String w) {
        return new ColumnConfig(fieldName, sortable, filterable, w, align, formatter);
    }

    /**
     * Return a new ColumnConfig with the specified alignment.
     *
     * @param a Text alignment
     */
    public ColumnConfig withAlign(TextAlign a) {
        return new ColumnConfig(fieldName, sortable, filterable, width, a, formatter);
    }

    /**
     * Return a new ColumnConfig with the specified formatter.
     *
     * @param f Function to format cell values
     */
    public ColumnConfig withFormatter(Function<Object, String> f) {
        return new ColumnConfig(fieldName, sortable, filterable, width, align, f);
    }
}
