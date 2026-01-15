package rsp.compositions.ui;

import rsp.component.ComponentView;
import rsp.compositions.EditView;
import rsp.compositions.ListSchema;
import rsp.dsl.Definition;
import rsp.ref.ElementRef;
import rsp.util.json.JsonDataType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static rsp.compositions.EventKeys.*;
import static rsp.dsl.Html.*;

/**
 * DefaultEditView - Adaptive form view implementation.
 * <p>
 * Renders form fields for ANY entity based on schema metadata.
 * Supports any number of fields and types.
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
            // Create element refs for all input fields
            final Map<String, ElementRef> fieldRefs = new HashMap<>();
            for (ListSchema.ColumnDef column : state.schema().columns()) {
                fieldRefs.put(column.name(), createElementRef());
            }

            // Determine title based on create/edit mode
            final String title = state.isCreateMode() ? "Create Item" : "Edit Item";

            return div(
                h1(text(title)),

                form(
                    // Render fields dynamically based on schema
                    of(state.schema().columns().stream()
                        .map(column -> renderField(column, state.fieldValues().get(column.name()), fieldRefs.get(column.name())))
                    ),

                    // Action buttons
                    div(attr("class", "form-actions"),
                        button(
                            attr("type", "button"),
                            text("Save"),
                            on("click", ctx -> {
                                // Collect field values from input elements asynchronously
                                Map<String, java.util.concurrent.CompletableFuture<Object>> futureValues = new HashMap<>();

                                for (Map.Entry<String, ElementRef> entry : fieldRefs.entrySet()) {
                                    String fieldName = entry.getKey();
                                    ElementRef ref = entry.getValue();

                                    // Get the column definition to know the type
                                    ListSchema.ColumnDef column = state.schema().columns().stream()
                                        .filter(c -> c.name().equals(fieldName))
                                        .findFirst()
                                        .orElse(null);

                                    if (column != null) {
                                        // Get property value asynchronously
                                        // For checkboxes, read "checked" property; for other inputs, read "value"
                                        String property = (column.type() == Boolean.class || column.type() == boolean.class)
                                            ? "checked"
                                            : "value";

                                        futureValues.put(
                                            fieldName,
                                            ctx.propertiesByRef(ref).get(property)
                                                .thenApply(json -> convertJsonValue(json, column.type()))
                                        );
                                    }
                                }

                                // Wait for all values to be collected, then send action
                                java.util.concurrent.CompletableFuture.allOf(
                                    futureValues.values().toArray(new java.util.concurrent.CompletableFuture[0])
                                ).thenRun(() -> {
                                    Map<String, Object> collectedValues = new HashMap<>();
                                    futureValues.forEach((name, future) -> {
                                        try {
                                            collectedValues.put(name, future.get());
                                        } catch (Exception e) {
                                            // Use default value on error
                                            ListSchema.ColumnDef col = state.schema().columns().stream()
                                                .filter(c -> c.name().equals(name))
                                                .findFirst()
                                                .orElse(null);
                                            if (col != null) {
                                                collectedValues.put(name, getDefaultValue(col.type()));
                                            }
                                        }
                                    });

                                    // Emit form.submitted event with collected field values
                                    // Contract will decide what to do (save, validate, etc.)
                                    commandsEnqueue.offer(FORM_SUBMITTED.emit(collectedValues));
                                });
                            })
                        ),
                        a(
                            attr("href", state.listRoute()),
                            attr("class", "button cancel-button"),
                            text("Cancel")
                        ),

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
                                            commandsEnqueue.offer(DELETE_REQUESTED.emit());
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
     * Render a form field based on column definition and current value.
     */
    private Definition renderField(ListSchema.ColumnDef column, Object currentValue, ElementRef fieldRef) {
        return div(attr("class", "form-field"),
            label(
                attr("for", column.name()),
                text(column.displayName())
            ),
            renderInput(column, currentValue, fieldRef)
        );
    }

    /**
     * Render appropriate input element based on field type.
     */
    private Definition renderInput(ListSchema.ColumnDef column, Object currentValue, ElementRef fieldRef) {
        String valueStr = currentValue != null ? currentValue.toString() : "";

        // Boolean fields -> checkbox
        if (column.type() == Boolean.class || column.type() == boolean.class) {
            return input(
                attr("type", "checkbox"),
                attr("id", column.name()),
                attr("name", column.name()),
                currentValue != null && (Boolean) currentValue ? attr("checked") : of(),
                elementId(fieldRef)
            );
        }

        // Date fields -> date input
        if (column.type() == LocalDate.class) {
            return input(
                attr("type", "date"),
                attr("id", column.name()),
                attr("name", column.name()),
                prop("value", valueStr),
                elementId(fieldRef)
            );
        }

        // DateTime fields -> datetime-local input
        if (column.type() == LocalDateTime.class) {
            return input(
                attr("type", "datetime-local"),
                attr("id", column.name()),
                attr("name", column.name()),
                prop("value", valueStr),
                elementId(fieldRef)
            );
        }

        // Number fields -> number input
        if (isNumericType(column.type())) {
            return input(
                attr("type", "number"),
                attr("id", column.name()),
                attr("name", column.name()),
                prop("value", valueStr),
                elementId(fieldRef)
            );
        }

        // Default: text input
        return input(
            attr("type", "text"),
            attr("id", column.name()),
            attr("name", column.name()),
            prop("value", valueStr),
            elementId(fieldRef)
        );
    }

    /**
     * Check if a type is numeric.
     */
    private boolean isNumericType(Class<?> type) {
        return type == Integer.class || type == int.class
            || type == Long.class || type == long.class
            || type == Double.class || type == double.class
            || type == Float.class || type == float.class;
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
