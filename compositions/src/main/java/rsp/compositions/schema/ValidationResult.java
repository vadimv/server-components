package rsp.compositions.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of field or schema validation.
 * <p>
 * Contains validation status and field-specific error messages.
 * Supports merging multiple results for aggregate validation.
 */
public record ValidationResult(
    boolean valid,
    Map<String, List<String>> errors
) {
    public ValidationResult {
        errors = errors != null ? Map.copyOf(errors) : Map.of();
    }

    /**
     * Create a successful validation result.
     */
    public static ValidationResult success() {
        return new ValidationResult(true, Map.of());
    }

    /**
     * Create a failed validation result for a single field.
     *
     * @param fieldName The field that failed validation
     * @param message The error message
     */
    public static ValidationResult failure(String fieldName, String message) {
        return new ValidationResult(false, Map.of(fieldName, List.of(message)));
    }

    /**
     * Check if validation passed.
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Get error messages for a specific field.
     *
     * @param fieldName The field name
     * @return List of error messages (empty if no errors)
     */
    public List<String> errorsFor(String fieldName) {
        return errors.getOrDefault(fieldName, List.of());
    }

    /**
     * Merge this result with another, combining errors.
     *
     * @param other The other validation result
     * @return New merged result
     */
    public ValidationResult merge(ValidationResult other) {
        if (this.valid && other.valid) {
            return success();
        }

        Map<String, List<String>> merged = new HashMap<>(this.errors);
        for (Map.Entry<String, List<String>> entry : other.errors.entrySet()) {
            merged.merge(entry.getKey(), entry.getValue(), (existing, newErrors) -> {
                List<String> combined = new ArrayList<>(existing);
                combined.addAll(newErrors);
                return combined;
            });
        }

        return new ValidationResult(false, merged);
    }
}
