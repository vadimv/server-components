package rsp.compositions.schema;

import java.util.Map;

/**
 * Interface for field validation.
 * <p>
 * Validators check field values and return validation results.
 * They can also provide HTML5 attributes for client-side validation hints.
 */
@FunctionalInterface
public interface Validator {

    /**
     * Validate a field value.
     *
     * @param fieldName The name of the field being validated
     * @param value The value to validate (may be null)
     * @return ValidationResult indicating success or failure with message
     */
    ValidationResult validate(String fieldName, Object value);

    /**
     * Get HTML5 validation attributes for client-side hints.
     * <p>
     * Default implementation returns empty map (no client-side hints).
     * Override to provide attributes like "required", "maxlength", "pattern".
     *
     * @return Map of HTML attribute name to value
     */
    default Map<String, String> htmlAttributes() {
        return Map.of();
    }
}
