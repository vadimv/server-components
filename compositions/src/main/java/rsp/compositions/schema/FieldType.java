package rsp.compositions.schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Semantic field types for schema definitions.
 * <p>
 * FieldType represents the logical meaning of a field, which influences
 * both validation behavior and default widget selection.
 */
public enum FieldType {
    /**
     * Entity identifier - typically hidden in forms, shown in lists.
     */
    ID,

    /**
     * Single-line text input.
     */
    STRING,

    /**
     * Multi-line text input (textarea).
     */
    TEXT,

    /**
     * Whole number (int, long).
     */
    INTEGER,

    /**
     * Decimal number (double, float).
     */
    DECIMAL,

    /**
     * True/false value.
     */
    BOOLEAN,

    /**
     * Date only (no time component).
     */
    DATE,

    /**
     * Date and time.
     */
    DATETIME,

    /**
     * Enumerated values (dropdown/select).
     */
    ENUM;

    /**
     * Infer the FieldType from a Java class.
     *
     * @param javaType The Java class to infer from
     * @return The inferred FieldType
     */
    public static FieldType fromJavaType(Class<?> javaType) {
        if (javaType == null) {
            return STRING;
        }

        if (javaType == Boolean.class || javaType == boolean.class) {
            return BOOLEAN;
        }

        if (javaType == Integer.class || javaType == int.class ||
            javaType == Long.class || javaType == long.class) {
            return INTEGER;
        }

        if (javaType == Double.class || javaType == double.class ||
            javaType == Float.class || javaType == float.class) {
            return DECIMAL;
        }

        if (javaType == LocalDate.class) {
            return DATE;
        }

        if (javaType == LocalDateTime.class) {
            return DATETIME;
        }

        if (javaType.isEnum()) {
            return ENUM;
        }

        return STRING;
    }

    /**
     * Get the default Java type for this FieldType.
     *
     * @return The default Java class
     */
    public Class<?> defaultJavaType() {
        return switch (this) {
            case ID, STRING, TEXT -> String.class;
            case INTEGER -> Integer.class;
            case DECIMAL -> Double.class;
            case BOOLEAN -> Boolean.class;
            case DATE -> LocalDate.class;
            case DATETIME -> LocalDateTime.class;
            case ENUM -> String.class;
        };
    }
}
