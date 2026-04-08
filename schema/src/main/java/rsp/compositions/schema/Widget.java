package rsp.compositions.schema;

/**
 * UI widget hints for field rendering.
 * <p>
 * Widget specifies how a field should be rendered in forms.
 * Views use this hint to select the appropriate input element.
 */
public enum Widget {
    /**
     * Standard single-line text input.
     */
    TEXT,

    /**
     * Multi-line text area.
     */
    TEXTAREA,

    /**
     * Password input (masked characters).
     */
    PASSWORD,

    /**
     * Checkbox for boolean values.
     */
    CHECKBOX,

    /**
     * Dropdown select for enumerated values.
     */
    SELECT,

    /**
     * Radio button group for enumerated values.
     */
    RADIO,

    /**
     * Date picker input.
     */
    DATE_PICKER,

    /**
     * Number input with spinner.
     */
    NUMBER,

    /**
     * Hidden input (not rendered visually).
     */
    HIDDEN;

    /**
     * Infer the default Widget from a FieldType.
     *
     * @param fieldType The field type
     * @return The default widget for that type
     */
    public static Widget fromFieldType(FieldType fieldType) {
        return switch (fieldType) {
            case ID -> HIDDEN;
            case STRING -> TEXT;
            case TEXT -> TEXTAREA;
            case INTEGER, DECIMAL -> NUMBER;
            case BOOLEAN -> CHECKBOX;
            case DATE, DATETIME -> DATE_PICKER;
            case ENUM -> SELECT;
        };
    }
}
