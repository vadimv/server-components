package rsp.compositions.schema;

import java.util.List;

/**
 * Immutable options record for field configuration.
 * <p>
 * Contains all optional field metadata: constraints, display hints, and defaults.
 * Uses builder-style with* methods for immutable updates.
 */
public record FieldOptions(
    boolean required,
    boolean hidden,
    boolean readOnly,
    Integer maxLength,
    Integer minLength,
    String placeholder,
    String format,
    Object defaultValue,
    List<String> enumOptions
) {
    public FieldOptions {
        enumOptions = enumOptions != null ? List.copyOf(enumOptions) : List.of();
    }

    /**
     * Create default options (all constraints off, no hints).
     */
    public static FieldOptions defaults() {
        return new FieldOptions(
            false,    // required
            false,    // hidden
            false,    // readOnly
            null,     // maxLength
            null,     // minLength
            null,     // placeholder
            null,     // format
            null,     // defaultValue
            List.of() // enumOptions
        );
    }

    public FieldOptions withRequired(boolean required) {
        return new FieldOptions(required, hidden, readOnly, maxLength, minLength,
            placeholder, format, defaultValue, enumOptions);
    }

    public FieldOptions withHidden(boolean hidden) {
        return new FieldOptions(required, hidden, readOnly, maxLength, minLength,
            placeholder, format, defaultValue, enumOptions);
    }

    public FieldOptions withReadOnly(boolean readOnly) {
        return new FieldOptions(required, hidden, readOnly, maxLength, minLength,
            placeholder, format, defaultValue, enumOptions);
    }

    public FieldOptions withMaxLength(Integer maxLength) {
        return new FieldOptions(required, hidden, readOnly, maxLength, minLength,
            placeholder, format, defaultValue, enumOptions);
    }

    public FieldOptions withMinLength(Integer minLength) {
        return new FieldOptions(required, hidden, readOnly, maxLength, minLength,
            placeholder, format, defaultValue, enumOptions);
    }

    public FieldOptions withPlaceholder(String placeholder) {
        return new FieldOptions(required, hidden, readOnly, maxLength, minLength,
            placeholder, format, defaultValue, enumOptions);
    }

    public FieldOptions withFormat(String format) {
        return new FieldOptions(required, hidden, readOnly, maxLength, minLength,
            placeholder, format, defaultValue, enumOptions);
    }

    public FieldOptions withDefaultValue(Object defaultValue) {
        return new FieldOptions(required, hidden, readOnly, maxLength, minLength,
            placeholder, format, defaultValue, enumOptions);
    }

    public FieldOptions withEnumOptions(List<String> enumOptions) {
        return new FieldOptions(required, hidden, readOnly, maxLength, minLength,
            placeholder, format, defaultValue, enumOptions);
    }
}
