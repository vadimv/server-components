package rsp.compositions.schema;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Factory for common field validators.
 * <p>
 * Provides pre-built validators for common validation scenarios.
 * Each validator includes both server-side validation logic and
 * optional HTML5 attributes for client-side hints.
 */
public final class Validators {
    private Validators() {}

    /**
     * Validates that a field is not null or blank.
     */
    public static Validator required() {
        return new Validator() {
            @Override
            public ValidationResult validate(String fieldName, Object value) {
                if (value == null) {
                    return ValidationResult.failure(fieldName, fieldName + " is required");
                }
                if (value instanceof String s && s.isBlank()) {
                    return ValidationResult.failure(fieldName, fieldName + " is required");
                }
                return ValidationResult.success();
            }

            @Override
            public Map<String, String> htmlAttributes() {
                return Map.of("required", "required");
            }
        };
    }

    /**
     * Validates that a string does not exceed maximum length.
     *
     * @param max Maximum allowed length
     */
    public static Validator maxLength(int max) {
        return new Validator() {
            @Override
            public ValidationResult validate(String fieldName, Object value) {
                if (value == null) {
                    return ValidationResult.success();
                }
                String str = value.toString();
                if (str.length() > max) {
                    return ValidationResult.failure(fieldName,
                        fieldName + " must be at most " + max + " characters");
                }
                return ValidationResult.success();
            }

            @Override
            public Map<String, String> htmlAttributes() {
                return Map.of("maxlength", String.valueOf(max));
            }
        };
    }

    /**
     * Validates that a string meets minimum length.
     *
     * @param min Minimum required length
     */
    public static Validator minLength(int min) {
        return new Validator() {
            @Override
            public ValidationResult validate(String fieldName, Object value) {
                if (value == null) {
                    return ValidationResult.success(); // Use required() for null check
                }
                String str = value.toString();
                if (str.length() < min) {
                    return ValidationResult.failure(fieldName,
                        fieldName + " must be at least " + min + " characters");
                }
                return ValidationResult.success();
            }

            @Override
            public Map<String, String> htmlAttributes() {
                return Map.of("minlength", String.valueOf(min));
            }
        };
    }

    /**
     * Validates that a string matches a regex pattern.
     *
     * @param regex The regular expression pattern
     */
    public static Validator pattern(String regex) {
        Pattern compiled = Pattern.compile(regex);
        return new Validator() {
            @Override
            public ValidationResult validate(String fieldName, Object value) {
                if (value == null) {
                    return ValidationResult.success();
                }
                String str = value.toString();
                if (!compiled.matcher(str).matches()) {
                    return ValidationResult.failure(fieldName,
                        fieldName + " does not match the required format");
                }
                return ValidationResult.success();
            }

            @Override
            public Map<String, String> htmlAttributes() {
                return Map.of("pattern", regex);
            }
        };
    }

    /**
     * Validates that a string is a valid email address.
     */
    public static Validator email() {
        // Simple email pattern - allows most valid emails
        String emailRegex = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
        Pattern compiled = Pattern.compile(emailRegex);
        return new Validator() {
            @Override
            public ValidationResult validate(String fieldName, Object value) {
                if (value == null) {
                    return ValidationResult.success();
                }
                String str = value.toString();
                if (str.isEmpty()) {
                    return ValidationResult.success();
                }
                if (!compiled.matcher(str).matches()) {
                    return ValidationResult.failure(fieldName,
                        fieldName + " must be a valid email address");
                }
                return ValidationResult.success();
            }

            @Override
            public Map<String, String> htmlAttributes() {
                return Map.of("type", "email");
            }
        };
    }

    /**
     * Validates that a number is within a range.
     *
     * @param min Minimum value (inclusive)
     * @param max Maximum value (inclusive)
     */
    public static Validator range(Number min, Number max) {
        return new Validator() {
            @Override
            public ValidationResult validate(String fieldName, Object value) {
                if (value == null) {
                    return ValidationResult.success();
                }

                double numValue;
                if (value instanceof Number n) {
                    numValue = n.doubleValue();
                } else {
                    try {
                        numValue = Double.parseDouble(value.toString());
                    } catch (NumberFormatException e) {
                        return ValidationResult.failure(fieldName,
                            fieldName + " must be a number");
                    }
                }

                if (numValue < min.doubleValue() || numValue > max.doubleValue()) {
                    return ValidationResult.failure(fieldName,
                        fieldName + " must be between " + min + " and " + max);
                }
                return ValidationResult.success();
            }

            @Override
            public Map<String, String> htmlAttributes() {
                return Map.of(
                    "min", min.toString(),
                    "max", max.toString()
                );
            }
        };
    }

    /**
     * Validates that a number meets a minimum value.
     *
     * @param min Minimum value (inclusive)
     */
    public static Validator min(Number min) {
        return new Validator() {
            @Override
            public ValidationResult validate(String fieldName, Object value) {
                if (value == null) {
                    return ValidationResult.success();
                }

                double numValue;
                if (value instanceof Number n) {
                    numValue = n.doubleValue();
                } else {
                    try {
                        numValue = Double.parseDouble(value.toString());
                    } catch (NumberFormatException e) {
                        return ValidationResult.failure(fieldName,
                            fieldName + " must be a number");
                    }
                }

                if (numValue < min.doubleValue()) {
                    return ValidationResult.failure(fieldName,
                        fieldName + " must be at least " + min);
                }
                return ValidationResult.success();
            }

            @Override
            public Map<String, String> htmlAttributes() {
                return Map.of("min", min.toString());
            }
        };
    }

    /**
     * Validates that a number does not exceed a maximum value.
     *
     * @param max Maximum value (inclusive)
     */
    public static Validator max(Number max) {
        return new Validator() {
            @Override
            public ValidationResult validate(String fieldName, Object value) {
                if (value == null) {
                    return ValidationResult.success();
                }

                double numValue;
                if (value instanceof Number n) {
                    numValue = n.doubleValue();
                } else {
                    try {
                        numValue = Double.parseDouble(value.toString());
                    } catch (NumberFormatException e) {
                        return ValidationResult.failure(fieldName,
                            fieldName + " must be a number");
                    }
                }

                if (numValue > max.doubleValue()) {
                    return ValidationResult.failure(fieldName,
                        fieldName + " must be at most " + max);
                }
                return ValidationResult.success();
            }

            @Override
            public Map<String, String> htmlAttributes() {
                return Map.of("max", max.toString());
            }
        };
    }
}
