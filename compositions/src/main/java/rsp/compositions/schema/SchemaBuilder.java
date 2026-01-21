package rsp.compositions.schema;

import rsp.compositions.DataSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point for building DataSchema using the fluent DSL.
 * <p>
 * Usage:
 * <pre>{@code
 * DataSchema schema = DataSchema.builder()
 *     .field("id", FieldType.ID).hidden()
 *     .field("title", FieldType.STRING).required().maxLength(200)
 *     .field("content", FieldType.TEXT).widget(Widget.TEXTAREA)
 *     .column("title").sortable().filterable().width("40%")
 *     .column("content").sortable()
 *     .build();
 * }</pre>
 *
 * @see FieldBuilder
 * @see DataSchema#builder()
 */
public class SchemaBuilder {
    private final List<FieldDef> fields = new ArrayList<>();
    private final Map<String, ColumnConfig> columnConfigs = new LinkedHashMap<>();
    private boolean selectable = false;

    /**
     * Start defining a new field.
     *
     * @param name The field name (must match entity property name)
     * @param fieldType The semantic type of the field
     * @return A FieldBuilder for configuring the field
     */
    public FieldBuilder field(String name, FieldType fieldType) {
        return new FieldBuilder(this, name, fieldType);
    }

    /**
     * Start configuring a column for list display.
     * <p>
     * Column configuration is optional - fields without explicit column config
     * will use defaults (non-sortable, non-filterable, left-aligned).
     *
     * @param fieldName The field name to configure as a column
     * @return A ColumnBuilder for configuring the column
     */
    public ColumnBuilder column(String fieldName) {
        return new ColumnBuilder(this, fieldName);
    }

    /**
     * Enable row selection for list views.
     * <p>
     * When enabled, list views will render a checkbox column for selecting rows.
     * Selected row IDs are stored in the list view's state and can be used
     * for bulk actions like delete.
     *
     * @return This builder for chaining
     */
    public SchemaBuilder selectable() {
        this.selectable = true;
        return this;
    }

    /**
     * Add a completed field definition.
     * Called internally by FieldBuilder.
     */
    void addField(FieldDef field) {
        this.fields.add(field);
    }

    /**
     * Add a column configuration.
     * Called internally by ColumnBuilder.
     */
    void addColumnConfig(String fieldName, ColumnConfig config) {
        this.columnConfigs.put(fieldName, config);
    }

    /**
     * Build the DataSchema with all defined fields and column configs.
     * Called internally by FieldBuilder.build() or ColumnBuilder.build().
     */
    DataSchema build() {
        return DataSchema.fromFields(fields, columnConfigs, selectable);
    }

    /**
     * Set the selectable flag.
     * Called internally by ColumnBuilder and FieldBuilder.
     */
    void setSelectable(boolean selectable) {
        this.selectable = selectable;
    }
}
