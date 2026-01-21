package rsp.compositions.schema;

import rsp.compositions.DataSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for configuring a single field.
 * <p>
 * Provides chainable methods for setting field properties, validators, and options.
 * Terminal methods allow transitioning to the next field or building the schema.
 * <p>
 * Example:
 * <pre>{@code
 * DataSchema.builder()
 *     .field("title", FieldType.STRING)
 *         .label("Post Title")
 *         .required()
 *         .maxLength(200)
 *         .placeholder("Enter title...")
 *     .field("content", FieldType.TEXT)
 *         .widget(Widget.TEXTAREA)
 *     .build();
 * }</pre>
 */
public class FieldBuilder {
    private final SchemaBuilder parent;
    private final String name;
    private final FieldType fieldType;

    private String displayName;
    private Class<?> type;
    private Widget widget;
    private final List<Validator> validators = new ArrayList<>();
    private FieldOptions options = FieldOptions.defaults();

    FieldBuilder(SchemaBuilder parent, String name, FieldType fieldType) {
        this.parent = parent;
        this.name = name;
        this.fieldType = fieldType;
        this.displayName = null; // Will use default formatting
        this.type = fieldType.defaultJavaType();
        this.widget = Widget.fromFieldType(fieldType);
    }

    /**
     * Set the display label for this field.
     */
    public FieldBuilder label(String label) {
        this.displayName = label;
        return this;
    }

    /**
     * Mark this field as required.
     */
    public FieldBuilder required() {
        this.validators.add(Validators.required());
        this.options = options.withRequired(true);
        return this;
    }

    /**
     * Mark this field as hidden (not rendered in forms).
     */
    public FieldBuilder hidden() {
        this.options = options.withHidden(true);
        this.widget = Widget.HIDDEN;
        return this;
    }

    /**
     * Mark this field as read-only.
     */
    public FieldBuilder readOnly() {
        this.options = options.withReadOnly(true);
        return this;
    }

    /**
     * Set maximum length constraint.
     */
    public FieldBuilder maxLength(int max) {
        this.validators.add(Validators.maxLength(max));
        this.options = options.withMaxLength(max);
        return this;
    }

    /**
     * Set minimum length constraint.
     */
    public FieldBuilder minLength(int min) {
        this.validators.add(Validators.minLength(min));
        this.options = options.withMinLength(min);
        return this;
    }

    /**
     * Set placeholder text for the input.
     */
    public FieldBuilder placeholder(String placeholder) {
        this.options = options.withPlaceholder(placeholder);
        return this;
    }

    /**
     * Set the UI widget for this field.
     */
    public FieldBuilder widget(Widget widget) {
        this.widget = widget;
        return this;
    }

    /**
     * Add a custom validator.
     */
    public FieldBuilder validate(Validator validator) {
        this.validators.add(validator);
        return this;
    }

    /**
     * Set enum options for SELECT/RADIO widgets.
     */
    public FieldBuilder options(String... options) {
        this.options = this.options.withEnumOptions(List.of(options));
        return this;
    }

    /**
     * Set the default value for this field.
     */
    public FieldBuilder defaultValue(Object value) {
        this.options = options.withDefaultValue(value);
        return this;
    }

    /**
     * Set format pattern (for dates, numbers, etc.).
     */
    public FieldBuilder format(String format) {
        this.options = options.withFormat(format);
        return this;
    }

    /**
     * Override the Java type for this field.
     */
    public FieldBuilder javaType(Class<?> type) {
        this.type = type;
        return this;
    }

    // ========== Terminal Methods ==========

    /**
     * Start defining the next field.
     * Adds the current field to the schema and returns a new FieldBuilder.
     */
    public FieldBuilder field(String name, FieldType fieldType) {
        parent.addField(buildDef());
        return new FieldBuilder(parent, name, fieldType);
    }

    /**
     * Start configuring a column for list display.
     * Adds the current field and switches to column configuration.
     *
     * @param fieldName The field name to configure as a column
     * @return A ColumnBuilder for configuring the column
     */
    public ColumnBuilder column(String fieldName) {
        parent.addField(buildDef());
        return new ColumnBuilder(parent, fieldName);
    }

    /**
     * Enable row selection for list views.
     * <p>
     * When enabled, list views will render a checkbox column for selecting rows.
     *
     * @return The SchemaBuilder for further configuration or building
     */
    public SchemaBuilder selectable() {
        parent.addField(buildDef());
        return parent.selectable();
    }

    /**
     * Build the final DataSchema.
     * Adds the current field and returns the completed schema.
     */
    public DataSchema build() {
        parent.addField(buildDef());
        return parent.build();
    }

    /**
     * Build the FieldDef for this field.
     */
    FieldDef buildDef() {
        return new FieldDef(
            name,
            displayName,
            type,
            fieldType,
            widget,
            validators,
            options
        );
    }
}
