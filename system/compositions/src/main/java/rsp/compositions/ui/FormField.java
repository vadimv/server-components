package rsp.compositions.ui;

import rsp.compositions.schema.FieldDef;
import rsp.dsl.Definition;

import java.util.List;

import static rsp.dsl.Html.*;

/**
 * Reusable form field component.
 * <p>
 * Renders a complete form field with:
 * <ul>
 *   <li>Label with required marker</li>
 *   <li>Input element (passed in)</li>
 *   <li>Validation error messages</li>
 * </ul>
 * <p>
 * Can be used standalone or within DefaultEditView.
 *
 * <p>Example usage:
 * <pre>{@code
 * import static rsp.compositions.ui.FormField.formField;
 *
 * formField(fieldDef, input(attr("type", "text")), List.of("Error message"));
 * }</pre>
 */
public final class FormField {
    private FormField() {}

    /**
     * Render a complete form field with label, input, and errors.
     *
     * @param field The field definition
     * @param input The input element (already rendered)
     * @param errors List of validation error messages (empty if valid)
     * @return The complete form field definition
     */
    public static Definition formField(FieldDef field, Definition input, List<String> errors) {
        return formField(field, input, errors, field.name(), field.name() + "-errors");
    }

    /** Render a field with instance-unique input and error identifiers. */
    public static Definition formField(FieldDef field,
                                       Definition input,
                                       List<String> errors,
                                       String inputId,
                                       String errorId) {
        boolean hasErrors = errors != null && !errors.isEmpty();

        return div(attr("class", "form-field"
                        + (field.isRequired() ? " required" : "")
                        + (hasErrors ? " has-error" : "")),
            label(
                attr("for", inputId),
                text(field.displayName()),
                field.isRequired() ? span(attr("class", "required-marker"),
                        attr("aria-hidden", "true"), text(" *")) : of(),
                field.isRequired() ? span(attr("class", "sr-only"), text(" (required)")) : of()
            ),
            input,
            renderErrors(errors, errorId)
        );
    }

    /**
     * Render validation error messages.
     * <p>
     * Can be used standalone when you need just the error messages
     * without the full form field wrapper.
     *
     * @param errors List of error messages
     * @return Definition containing error message elements, or empty if no errors
     */
    public static Definition renderErrors(List<String> errors) {
        return renderErrors(errors, null);
    }

    /** Render validation errors with an optional ID for {@code aria-describedby}. */
    public static Definition renderErrors(List<String> errors, String errorId) {
        if (errors == null || errors.isEmpty()) {
            return of();
        }
        return div(attr("class", "field-errors"),
            errorId == null ? of() : attr("id", errorId),
            of(errors.stream().map(err ->
                span(attr("class", "field-error"), text(err))
            ))
        );
    }
}
