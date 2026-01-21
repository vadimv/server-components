package rsp.compositions.schema;

import rsp.compositions.DataSchema;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Enhanced field definition with validation and UI hints.
 * <p>
 * FieldDef extends the basic ColumnDef with:
 * <ul>
 *   <li>Semantic field type (FieldType)</li>
 *   <li>UI widget hint (Widget)</li>
 *   <li>Validation rules (List of Validator)</li>
 *   <li>Field options (placeholder, format, defaults, etc.)</li>
 * </ul>
 * <p>
 * Provides conversion to/from legacy ColumnDef for backward compatibility.
 */
public record FieldDef(
    String name,
    String displayName,
    Class<?> type,
    FieldType fieldType,
    Widget widget,
    List<Validator> validators,
    FieldOptions options
) {
    public FieldDef {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Field name cannot be null or blank");
        }
        displayName = displayName != null ? displayName : formatDisplayName(name);
        type = type != null ? type : (fieldType != null ? fieldType.defaultJavaType() : String.class);
        fieldType = fieldType != null ? fieldType : FieldType.fromJavaType(type);
        widget = widget != null ? widget : Widget.fromFieldType(fieldType);
        validators = validators != null ? List.copyOf(validators) : List.of();
        options = options != null ? options : FieldOptions.defaults();
    }

    /**
     * Convert from legacy ColumnDef.
     * <p>
     * Infers FieldType and Widget from the Java type.
     *
     * @param column The ColumnDef to convert
     * @return A new FieldDef with inferred settings
     */
    public static FieldDef fromColumnDef(DataSchema.ColumnDef column) {
        FieldType fieldType = FieldType.fromJavaType(column.type());
        Widget widget = Widget.fromFieldType(fieldType);
        return new FieldDef(
            column.name(),
            column.displayName(),
            column.type(),
            fieldType,
            widget,
            List.of(),
            FieldOptions.defaults()
        );
    }

    /**
     * Convert to legacy ColumnDef for backward compatibility.
     *
     * @return A ColumnDef with basic field info
     */
    public DataSchema.ColumnDef toColumnDef() {
        return new DataSchema.ColumnDef(name, displayName, type);
    }

    /**
     * Validate a value using all validators on this field.
     *
     * @param value The value to validate
     * @return Combined validation result from all validators
     */
    public ValidationResult validate(Object value) {
        ValidationResult result = ValidationResult.success();
        for (Validator validator : validators) {
            result = result.merge(validator.validate(name, value));
        }
        return result;
    }

    /**
     * Get combined HTML5 validation attributes from all validators.
     *
     * @return Map of HTML attribute name to value
     */
    public Map<String, String> htmlValidationAttributes() {
        return validators.stream()
            .flatMap(v -> v.htmlAttributes().entrySet().stream())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (a, b) -> a // Keep first if duplicate
            ));
    }

    /**
     * Check if this field is required (has required validator or option set).
     */
    public boolean isRequired() {
        return options.required();
    }

    /**
     * Check if this field should be hidden.
     */
    public boolean isHidden() {
        return options.hidden() || widget == Widget.HIDDEN;
    }

    /**
     * Check if this field is read-only.
     */
    public boolean isReadOnly() {
        return options.readOnly();
    }

    /**
     * Format a field name as a display name.
     * Converts camelCase to Title Case.
     */
    private static String formatDisplayName(String name) {
        return name.substring(0, 1).toUpperCase() +
               name.substring(1).replaceAll("([A-Z])", " $1");
    }
}
