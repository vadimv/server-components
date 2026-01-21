package rsp.compositions.schema;

import rsp.compositions.DataSchema;

/**
 * Fluent builder for column configuration.
 * <p>
 * Used to configure list-specific display properties for fields.
 * Column configuration is optional - fields without explicit column config
 * will use defaults (non-sortable, non-filterable, left-aligned).
 * <p>
 * Example:
 * <pre>{@code
 * DataSchema schema = DataSchema.builder()
 *     .field("title", FieldType.STRING).required()
 *     .field("status", FieldType.STRING)
 *     .field("createdAt", FieldType.DATETIME)
 *     .column("title").sortable().filterable().width("40%")
 *     .column("status").sortable().filterable()
 *     .column("createdAt").sortable().align(TextAlign.RIGHT)
 *     .build();
 * }</pre>
 */
public class ColumnBuilder {
    private final SchemaBuilder schemaBuilder;
    private final String fieldName;
    private boolean sortable = false;
    private boolean filterable = false;
    private String width = null;
    private TextAlign align = TextAlign.LEFT;

    ColumnBuilder(SchemaBuilder schemaBuilder, String fieldName) {
        this.schemaBuilder = schemaBuilder;
        this.fieldName = fieldName;
    }

    /**
     * Make this column sortable.
     */
    public ColumnBuilder sortable() {
        this.sortable = true;
        return this;
    }

    /**
     * Make this column filterable.
     */
    public ColumnBuilder filterable() {
        this.filterable = true;
        return this;
    }

    /**
     * Set the column width.
     *
     * @param width CSS width (e.g., "200px", "30%")
     */
    public ColumnBuilder width(String width) {
        this.width = width;
        return this;
    }

    /**
     * Set the column text alignment.
     *
     * @param align Text alignment
     */
    public ColumnBuilder align(TextAlign align) {
        this.align = align;
        return this;
    }

    /**
     * Start configuring another column.
     *
     * @param fieldName The field name for the next column
     * @return A new ColumnBuilder for the next column
     */
    public ColumnBuilder column(String fieldName) {
        finishColumn();
        return new ColumnBuilder(schemaBuilder, fieldName);
    }

    /**
     * Switch back to defining fields.
     *
     * @param name Field name
     * @param fieldType Field type
     * @return A FieldBuilder for the new field
     */
    public FieldBuilder field(String name, FieldType fieldType) {
        finishColumn();
        return schemaBuilder.field(name, fieldType);
    }

    /**
     * Enable row selection for list views.
     * <p>
     * When enabled, list views will render a checkbox column for selecting rows.
     *
     * @return The SchemaBuilder for further configuration or building
     */
    public SchemaBuilder selectable() {
        finishColumn();
        return schemaBuilder.selectable();
    }

    /**
     * Build the final DataSchema.
     * Adds the current column config and returns the completed schema.
     */
    public DataSchema build() {
        finishColumn();
        return schemaBuilder.build();
    }

    /**
     * Complete the current column configuration.
     */
    private void finishColumn() {
        schemaBuilder.addColumnConfig(fieldName,
            new ColumnConfig(fieldName, sortable, filterable, width, align, null));
    }
}
