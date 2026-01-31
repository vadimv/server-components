package rsp.compositions.ui;

import rsp.component.ComponentView;
import rsp.compositions.schema.FieldDef;
import rsp.compositions.schema.ValidationResult;
import rsp.compositions.schema.Widget;
import rsp.dsl.Definition;
import rsp.ref.ElementRef;
import rsp.util.json.JsonDataType;

import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.EventKeys;
import rsp.compositions.contract.ViewContract;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static rsp.compositions.contract.EventKeys.*;
import static rsp.compositions.ui.FormField.formField;
import static rsp.dsl.Html.*;

/**
 * DefaultEditView - Adaptive form view implementation.
 * <p>
 * Renders form fields for ANY entity based on schema metadata.
 * Supports both legacy ColumnDef and new FieldDef with widgets and validators.
 * <p>
 * Emits events:
 * <ul>
 *   <li>"form.submitted" - Form data collected and ready for processing (payload: field values map)</li>
 *   <li>"delete.requested" - User confirmed delete action (payload: empty map)</li>
 * </ul>
 * <p>
 * Views only collect and validate data; Contracts decide what to do with it.
 */
public class DefaultEditView extends EditView {

    @Override
    public ComponentView<EditViewState> componentView() {
        return newState -> state -> {
            // Get fields - prefer FieldDef for enhanced rendering
            final List<FieldDef> fields = state.schema().fields();

            // Create element refs for all fields
            final Map<String, ElementRef> fieldRefs = new HashMap<>();
            for (FieldDef field : fields) {
                fieldRefs.put(field.name(), createElementRef());
            }

            return div(
                h1(text(state.title())),

                form(
                    // Render fields dynamically based on schema
                    of(fields.stream()
                        .map(field -> renderField(
                            field,
                            state.fieldValues().get(field.name()),
                            fieldRefs.get(field.name()),
                            state.errorsFor(field.name())
                        ))
                    ),

                    // Action buttons
                    div(attr("class", "form-actions"),
                        button(
                            attr("type", "button"),
                            text("Save"),
                            on("click", ctx -> {
                                // Collect field values from input elements asynchronously
                                Map<String, java.util.concurrent.CompletableFuture<Object>> futureValues = new HashMap<>();

                                for (FieldDef field : fields) {
                                    String fieldName = field.name();
                                    ElementRef ref = fieldRefs.get(fieldName);

                                    // Get property value asynchronously
                                    // For checkboxes, read "checked" property; for other inputs, read "value"
                                    String property = (field.widget() == Widget.CHECKBOX) ? "checked" : "value";

                                    futureValues.put(
                                        fieldName,
                                        ctx.propertiesByRef(ref).get(property)
                                            .thenApply(json -> convertJsonValue(json, field.type()))
                                    );
                                }

                                // Wait for all values to be collected, then validate and send action
                                java.util.concurrent.CompletableFuture.allOf(
                                    futureValues.values().toArray(new java.util.concurrent.CompletableFuture[0])
                                ).thenRun(() -> {
                                    Map<String, Object> collectedValues = new HashMap<>();
                                    futureValues.forEach((name, future) -> {
                                        try {
                                            collectedValues.put(name, future.get());
                                        } catch (Exception e) {
                                            // Use default value on error
                                            FieldDef fld = state.schema().field(name);
                                            if (fld != null) {
                                                collectedValues.put(name, getDefaultValue(fld.type()));
                                            }
                                        }
                                    });

                                    // Validate before submitting
                                    ValidationResult result = state.schema().validate(collectedValues);

                                    if (!result.isValid()) {
                                        // Update state with errors - triggers re-render with error messages
                                        newState.setState(new EditViewState(
                                            collectedValues,
                                            state.schema(),
                                            true,
                                            state.listRoute(),
                                            state.isCreateMode(),
                                            result.errors(),
                                            state.title()
                                        ));
                                    } else {
                                        // Clear any previous errors and submit
                                        newState.setState(new EditViewState(
                                            collectedValues,
                                            state.schema(),
                                            state.isDirty(),
                                            state.listRoute(),
                                            state.isCreateMode(),
                                            Map.of(),
                                            state.title()
                                        ));
                                        // Emit form.submitted event with collected field values
                                        // Contract will decide what to do (save, etc.)
                                        lookup.publish(FORM_SUBMITTED, collectedValues);
                                    }
                                });
                            })
                        ),
                        renderCancelButton(state.listRoute()),

                        // Delete button - only shown in edit mode (not create mode)
                        state.isCreateMode() ? of() : button(
                            attr("type", "button"),
                            attr("class", "btn-delete btn-danger"),
                            text("Delete"),
                            on("click", ctx -> {
                                // Client-side confirmation before delete
                                ctx.evalJs("confirm('Are you sure you want to delete this item?')")
                                    .thenAccept(result -> {
                                        if (result instanceof JsonDataType.Boolean confirmed && confirmed.value()) {
                                            // Emit delete.requested event
                                            // Contract will decide what to do
                                            lookup.publish(DELETE_REQUESTED);
                                        }
                                    });
                            })
                        )
                    )
                )
            );
        };
    }

    /**
     * Render the Cancel button.
     * <p>
     * Emits ACTION_SUCCESS(CANCEL) - framework derives behavior from composition config.
     * This follows the CountersMainComponent pattern: views emit INTENT, framework handles navigation.
     * <ul>
     *   <li>OVERLAY → framework emits HIDE + REFRESH_LIST</li>
     *   <li>PRIMARY → framework navigates to list route (derived from Router)</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private Definition renderCancelButton(String listRoute) {
        // Get contract class from context
        Class<? extends ViewContract> contractClass = lookup.get(ContextKeys.CONTRACT_CLASS);

        // Emit ACTION_SUCCESS(CANCEL) - framework derives behavior from composition
        return button(
            attr("type", "button"),
            attr("class", "button cancel-button"),
            text("Cancel"),
            on("click", ctx -> {
                if (contractClass != null) {
                    lookup.publish(ACTION_SUCCESS,
                        new EventKeys.ActionResult(contractClass, EventKeys.ActionType.CANCEL));
                }
            })
        );
    }

    /**
     * Render a form field based on FieldDef, current value, and validation errors.
     */
    private Definition renderField(FieldDef field, Object currentValue, ElementRef fieldRef, List<String> errors) {
        // Hidden fields render as hidden input (not wrapped in form-field div)
        if (field.isHidden()) {
            return renderHiddenInput(field, currentValue, fieldRef);
        }

        // Use FormField component for consistent rendering with error messages
        Definition input = renderInput(field, currentValue, fieldRef);
        return formField(field, input, errors);
    }

    /**
     * Render hidden input for hidden fields.
     */
    private Definition renderHiddenInput(FieldDef field, Object currentValue, ElementRef fieldRef) {
        String valueStr = currentValue != null ? currentValue.toString() : "";
        return input(
            attr("type", "hidden"),
            attr("id", field.name()),
            attr("name", field.name()),
            prop("value", valueStr),
            elementId(fieldRef)
        );
    }

    /**
     * Render appropriate input element based on Widget type.
     */
    private Definition renderInput(FieldDef field, Object currentValue, ElementRef fieldRef) {
        String valueStr = currentValue != null ? currentValue.toString() : "";

        // Get HTML5 validation attributes from validators
        Map<String, String> validationAttrs = field.htmlValidationAttributes();

        return switch (field.widget()) {
            case CHECKBOX -> input(
                attr("type", "checkbox"),
                attr("id", field.name()),
                attr("name", field.name()),
                currentValue != null && (Boolean) currentValue ? attr("checked") : of(),
                field.isReadOnly() ? attr("disabled") : of(),
                elementId(fieldRef)
            );

            case TEXTAREA -> textarea(
                attr("id", field.name()),
                attr("name", field.name()),
                field.options().placeholder() != null ? attr("placeholder", field.options().placeholder()) : of(),
                field.isReadOnly() ? attr("readonly") : of(),
                renderValidationAttrs(validationAttrs),
                text(valueStr),
                elementId(fieldRef)
            );

            case SELECT -> select(
                attr("id", field.name()),
                attr("name", field.name()),
                field.isReadOnly() ? attr("disabled") : of(),
                // Empty option for non-required fields
                !field.isRequired() ? option(attr("value", ""), text("-- Select --")) : of(),
                of(field.options().enumOptions().stream()
                    .map(opt -> option(
                        attr("value", opt),
                        opt.equals(valueStr) ? attr("selected") : of(),
                        text(opt)
                    ))
                ),
                elementId(fieldRef)
            );

            case PASSWORD -> input(
                attr("type", "password"),
                attr("id", field.name()),
                attr("name", field.name()),
                field.options().placeholder() != null ? attr("placeholder", field.options().placeholder()) : of(),
                field.isReadOnly() ? attr("readonly") : of(),
                renderValidationAttrs(validationAttrs),
                elementId(fieldRef)
            );

            case DATE_PICKER -> input(
                attr("type", field.type() == LocalDateTime.class ? "datetime-local" : "date"),
                attr("id", field.name()),
                attr("name", field.name()),
                prop("value", valueStr),
                field.isReadOnly() ? attr("readonly") : of(),
                elementId(fieldRef)
            );

            case NUMBER -> input(
                attr("type", "number"),
                attr("id", field.name()),
                attr("name", field.name()),
                prop("value", valueStr),
                field.options().placeholder() != null ? attr("placeholder", field.options().placeholder()) : of(),
                field.isReadOnly() ? attr("readonly") : of(),
                renderValidationAttrs(validationAttrs),
                elementId(fieldRef)
            );

            case HIDDEN -> renderHiddenInput(field, currentValue, fieldRef);

            default -> input(  // TEXT, RADIO (fallback to text)
                attr("type", "text"),
                attr("id", field.name()),
                attr("name", field.name()),
                prop("value", valueStr),
                field.options().placeholder() != null ? attr("placeholder", field.options().placeholder()) : of(),
                field.isReadOnly() ? attr("readonly") : of(),
                renderValidationAttrs(validationAttrs),
                elementId(fieldRef)
            );
        };
    }

    /**
     * Render HTML5 validation attributes.
     */
    private Definition renderValidationAttrs(Map<String, String> attrs) {
        if (attrs.isEmpty()) {
            return of();
        }
        return of(attrs.entrySet().stream()
            .map(e -> attr(e.getKey(), e.getValue()))
        );
    }

    /**
     * Convert JSON value from input to the appropriate type.
     */
    private Object convertJsonValue(JsonDataType json, Class<?> targetType) {
        // Handle boolean checkboxes specially
        if (targetType == Boolean.class || targetType == boolean.class) {
            if (json instanceof JsonDataType.Boolean bool) {
                return bool.value();
            }
            // Checkbox values can also come as strings "true"/"false" or be missing
            if (json instanceof JsonDataType.String str) {
                return Boolean.parseBoolean(str.value());
            }
            return false;
        }

        // For other types, extract string value and convert
        String stringValue = null;
        if (json instanceof JsonDataType.String str) {
            stringValue = str.value();
        } else if (json != null) {
            stringValue = json.toString();
        }

        return convertValue(stringValue, targetType);
    }

    /**
     * Convert string value from input to the appropriate type.
     */
    private Object convertValue(String stringValue, Class<?> targetType) {
        if (stringValue == null || stringValue.isEmpty()) {
            return getDefaultValue(targetType);
        }

        try {
            if (targetType == String.class) return stringValue;
            if (targetType == Integer.class || targetType == int.class) return Integer.parseInt(stringValue);
            if (targetType == Long.class || targetType == long.class) return Long.parseLong(stringValue);
            if (targetType == Double.class || targetType == double.class) return Double.parseDouble(stringValue);
            if (targetType == Float.class || targetType == float.class) return Float.parseFloat(stringValue);
            if (targetType == Boolean.class || targetType == boolean.class) return Boolean.parseBoolean(stringValue);
            if (targetType == LocalDate.class) return LocalDate.parse(stringValue);
            if (targetType == LocalDateTime.class) return LocalDateTime.parse(stringValue);
        } catch (Exception e) {
            // Return default value on parse error
            return getDefaultValue(targetType);
        }

        return stringValue;
    }

    /**
     * Get default value for a type.
     */
    private Object getDefaultValue(Class<?> type) {
        if (type == String.class) return "";
        if (type == Integer.class || type == int.class) return 0;
        if (type == Long.class || type == long.class) return 0L;
        if (type == Double.class || type == double.class) return 0.0;
        if (type == Float.class || type == float.class) return 0.0f;
        if (type == Boolean.class || type == boolean.class) return false;
        return null;
    }
}
