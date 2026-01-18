package rsp.compositions;

import rsp.compositions.schema.ColumnConfig;
import rsp.compositions.schema.FieldDef;
import rsp.compositions.schema.SchemaBuilder;
import rsp.compositions.schema.ValidationResult;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schema metadata for data views (lists and edit forms).
 * <p>
 * Provides field definitions that UI components use to render data adaptively.
 * Schema flows alongside data through ComponentContext, enabling UI components
 * to render any number of fields of any types.
 * <p>
 * Supports two modes:
 * <ul>
 *   <li><b>Auto-derivation:</b> {@link #fromRecordClass(Class)} for quick prototyping</li>
 *   <li><b>DSL-based:</b> {@link #builder()} for explicit field configuration with validators</li>
 * </ul>
 *
 * <p>Example DSL usage:
 * <pre>{@code
 * DataSchema schema = DataSchema.builder()
 *     .field("id", FieldType.ID).hidden()
 *     .field("title", FieldType.STRING).required().maxLength(200)
 *     .field("content", FieldType.TEXT).widget(Widget.TEXTAREA)
 *     .build();
 * }</pre>
 */
public record DataSchema(List<ColumnDef> columns, List<FieldDef> fields, Map<String, ColumnConfig> columnConfigs,
                         boolean selectable) {

    /**
     * Canonical constructor with validation.
     */
    public DataSchema {
        columns = columns != null ? List.copyOf(columns) : List.of();
        fields = fields != null ? List.copyOf(fields) : List.of();
        // Use LinkedHashMap to preserve insertion order for listColumns()
        columnConfigs = columnConfigs != null
            ? Collections.unmodifiableMap(new LinkedHashMap<>(columnConfigs))
            : Map.of();
    }

    /**
     * Backward-compatible constructor without selectable.
     */
    public DataSchema(List<ColumnDef> columns, List<FieldDef> fields, Map<String, ColumnConfig> columnConfigs) {
        this(columns, fields, columnConfigs, false);
    }

    /**
     * Backward-compatible constructor from columns only.
     * Automatically converts ColumnDefs to FieldDefs.
     */
    public DataSchema(List<ColumnDef> columns) {
        this(columns, columns.stream().map(FieldDef::fromColumnDef).toList(), Map.of(), false);
    }

    /**
     * Backward-compatible constructor without columnConfigs.
     */
    public DataSchema(List<ColumnDef> columns, List<FieldDef> fields) {
        this(columns, fields, Map.of(), false);
    }

    /**
     * Column definition with name, display name, and type information.
     * <p>
     * Retained for backward compatibility. New code should use {@link FieldDef}.
     */
    public record ColumnDef(String name, String displayName, Class<?> type) {
        public ColumnDef(String name, Class<?> type) {
            this(name, formatDisplayName(name), type);
        }

        private static String formatDisplayName(String name) {
            // Convert camelCase to Title Case
            return name.substring(0, 1).toUpperCase() +
                   name.substring(1).replaceAll("([A-Z])", " $1");
        }
    }

    // ========== Factory Methods ==========

    /**
     * Create a new schema builder for DSL-based field definition.
     *
     * @return A new SchemaBuilder instance
     */
    public static SchemaBuilder builder() {
        return new SchemaBuilder();
    }

    /**
     * Create a DataSchema from a list of FieldDefs.
     * Used internally by SchemaBuilder.
     *
     * @param fields The field definitions
     * @return A new DataSchema
     */
    public static DataSchema fromFields(List<FieldDef> fields) {
        return fromFields(fields, Map.of());
    }

    /**
     * Create a DataSchema from FieldDefs and column configs.
     * Used internally by SchemaBuilder.
     *
     * @param fields The field definitions
     * @param columnConfigs Map of field names to column configurations
     * @return A new DataSchema
     */
    public static DataSchema fromFields(List<FieldDef> fields, Map<String, ColumnConfig> columnConfigs) {
        return fromFields(fields, columnConfigs, false);
    }

    /**
     * Create a DataSchema from FieldDefs, column configs, and selectable flag.
     * Used internally by SchemaBuilder.
     *
     * @param fields The field definitions
     * @param columnConfigs Map of field names to column configurations
     * @param selectable Whether list rows are selectable
     * @return A new DataSchema
     */
    public static DataSchema fromFields(List<FieldDef> fields, Map<String, ColumnConfig> columnConfigs, boolean selectable) {
        List<ColumnDef> columns = fields.stream()
            .map(FieldDef::toColumnDef)
            .toList();
        return new DataSchema(columns, fields, columnConfigs, selectable);
    }

    /**
     * Extract schema from a Record class using reflection.
     *
     * @param recordClass The Record class to extract schema from
     * @return DataSchema with columns matching Record components
     */
    public static DataSchema fromRecordClass(Class<?> recordClass) {
        if (!recordClass.isRecord()) {
            throw new IllegalArgumentException("Class must be a Record type: " + recordClass.getName());
        }

        RecordComponent[] components = recordClass.getRecordComponents();
        List<ColumnDef> columns = new ArrayList<>();

        for (RecordComponent component : components) {
            columns.add(new ColumnDef(
                component.getName(),
                component.getType()
            ));
        }

        return new DataSchema(columns);
    }

    /**
     * Extract schema from the first item in a list by inspecting the actual object.
     * Works for Record types.
     *
     * @param item The first item to inspect
     * @return DataSchema derived from the item's type
     */
    public static DataSchema fromFirstItem(Object item) {
        if (item == null) {
            return new DataSchema(List.of());
        }

        return fromRecordClass(item.getClass());
    }

    // ========== Validation ==========

    /**
     * Validate a map of field values against this schema.
     *
     * @param values Map of field names to values
     * @return Combined validation result from all fields
     */
    public ValidationResult validate(Map<String, Object> values) {
        ValidationResult result = ValidationResult.success();
        for (FieldDef field : fields) {
            Object value = values.get(field.name());
            result = result.merge(field.validate(value));
        }
        return result;
    }

    // ========== Conversion Methods ==========

    /**
     * Convert a domain object to a Map representation based on this schema.
     *
     * @param item The domain object (must be a Record)
     * @return Map with column names as keys and field values
     */
    public Map<String, Object> toMap(Object item) {
        if (item == null) {
            return Map.of();
        }

        if (!item.getClass().isRecord()) {
            throw new IllegalArgumentException("Item must be a Record type: " + item.getClass().getName());
        }

        Map<String, Object> map = new LinkedHashMap<>();
        RecordComponent[] components = item.getClass().getRecordComponents();

        for (RecordComponent component : components) {
            try {
                String name = component.getName();
                Object value = component.getAccessor().invoke(item);
                map.put(name, value);
            } catch (Exception e) {
                throw new RuntimeException("Failed to extract field: " + component.getName(), e);
            }
        }

        return map;
    }

    /**
     * Convert a list of domain objects to Map representations.
     *
     * @param items List of domain objects
     * @return List of Maps with column data
     */
    public List<Map<String, Object>> toMapList(List<?> items) {
        return items.stream()
            .map(this::toMap)
            .toList();
    }

    // ========== Customization Methods ==========

    /**
     * Customize this schema by renaming a column's display name.
     */
    public DataSchema renameColumn(String columnName, String newDisplayName) {
        List<ColumnDef> newColumns = columns.stream()
            .map(col -> col.name().equals(columnName)
                ? new ColumnDef(col.name(), newDisplayName, col.type())
                : col)
            .toList();
        return new DataSchema(newColumns);
    }

    /**
     * Customize this schema by hiding a column.
     */
    public DataSchema hideColumn(String columnName) {
        List<ColumnDef> newColumns = columns.stream()
            .filter(col -> !col.name().equals(columnName))
            .toList();
        return new DataSchema(newColumns);
    }

    /**
     * Customize this schema by setting the selectable flag.
     *
     * @param selectable Whether list rows should be selectable
     * @return A new DataSchema with the selectable flag set
     */
    public DataSchema withSelectable(boolean selectable) {
        return new DataSchema(columns, fields, columnConfigs, selectable);
    }

    /**
     * Customize this schema by reordering columns.
     */
    public DataSchema reorderColumns(String... orderedNames) {
        Map<String, ColumnDef> columnMap = new LinkedHashMap<>();
        for (ColumnDef col : columns) {
            columnMap.put(col.name(), col);
        }

        List<ColumnDef> newColumns = new ArrayList<>();
        for (String name : orderedNames) {
            ColumnDef col = columnMap.get(name);
            if (col != null) {
                newColumns.add(col);
            }
        }

        return new DataSchema(newColumns);
    }

    // ========== Field Access ==========

    /**
     * Get a field definition by name.
     *
     * @param fieldName The field name
     * @return The FieldDef, or null if not found
     */
    public FieldDef field(String fieldName) {
        return fields.stream()
            .filter(f -> f.name().equals(fieldName))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get visible fields (non-hidden).
     *
     * @return List of visible FieldDefs
     */
    public List<FieldDef> visibleFields() {
        return fields.stream()
            .filter(f -> !f.isHidden())
            .toList();
    }

    // ========== Column Configuration ==========

    /**
     * Get column configuration for a field.
     * Returns a default config if no explicit config was defined.
     *
     * @param fieldName The field name
     * @return ColumnConfig for the field
     */
    public ColumnConfig columnConfig(String fieldName) {
        return columnConfigs.getOrDefault(fieldName, new ColumnConfig(fieldName));
    }

    /**
     * Get fields for list display, respecting explicit column ordering.
     * <p>
     * If column configs are defined, returns fields in the order columns were defined,
     * filtering out hidden fields. Otherwise, returns all visible fields.
     *
     * @return List of FieldDefs for list columns
     */
    public List<FieldDef> listColumns() {
        if (!columnConfigs.isEmpty()) {
            return columnConfigs.keySet().stream()
                .map(this::field)
                .filter(f -> f != null && !f.isHidden())
                .toList();
        }
        return visibleFields();
    }
}
