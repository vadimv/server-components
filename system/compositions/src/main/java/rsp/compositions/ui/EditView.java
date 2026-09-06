package rsp.compositions.ui;

import rsp.compositions.schema.DataSchema;
import rsp.compositions.block.FormCapabilities;
import rsp.compositions.block.FormMode;
import rsp.compositions.block.FormMutationResult;
import rsp.compositions.block.FormStatus;
import rsp.compositions.schema.FieldChoice;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** State and intents shared by form block components and edit views. */
public final class EditView {
    private EditView() {
    }

    public sealed interface EditIntent permits FormValuesCollected, FieldChanged, CancelRequested,
            DeleteConfirmed, DismissMessage {
    }

    public record FormValuesCollected(Map<String, Object> values) implements EditIntent {
        public FormValuesCollected {
            values = immutableValues(values);
        }
    }

    /** A browser or agent changed one draft field without submitting the form. */
    public record FieldChanged(String fieldName, Object value) implements EditIntent {
        public FieldChanged {
            if (fieldName == null || fieldName.isBlank()) {
                throw new IllegalArgumentException("fieldName is required");
            }
        }
    }

    public enum CancelRequested implements EditIntent {
        INSTANCE
    }

    public enum DeleteConfirmed implements EditIntent {
        INSTANCE
    }

    public enum DismissMessage implements EditIntent {
        INSTANCE
    }

    public record EditViewState(Map<String, Object> fieldValues,
                                DataSchema schema,
                                boolean isDirty,
                                String listRoute,
                                FormMode mode,
                                Map<String, List<String>> validationErrors,
                                String title,
                                FormCapabilities capabilities,
                                FormStatus status,
                                String message,
                                boolean error,
                                String formId,
                                Map<String, ChoiceSet> choiceSets) {
        public EditViewState {
            fieldValues = immutableValues(fieldValues);
            schema = schema == null ? new DataSchema(List.of()) : schema;
            listRoute = listRoute == null ? "/" : listRoute;
            mode = mode == null ? FormMode.EDIT : mode;
            validationErrors = immutableErrors(validationErrors);
            title = title == null ? (mode.isCreate() ? "Create Item" : "Edit Item") : title;
            capabilities = capabilities == null
                    ? (mode.isCreate() ? FormCapabilities.create() : FormCapabilities.edit())
                    : capabilities;
            status = status == null ? FormStatus.READY : status;
            message = message == null ? "" : message;
            formId = formId == null || formId.isBlank() ? "data-form" : formId;
            choiceSets = immutableChoiceSets(choiceSets);
        }

        /** Compatibility constructor retained for state producers without resolved field choices. */
        public EditViewState(Map<String, Object> fieldValues,
                             DataSchema schema,
                             boolean isDirty,
                             String listRoute,
                             FormMode mode,
                             Map<String, List<String>> validationErrors,
                             String title,
                             FormCapabilities capabilities,
                             FormStatus status,
                             String message,
                             boolean error,
                             String formId) {
            this(fieldValues, schema, isDirty, listRoute, mode, validationErrors, title, capabilities,
                    status, message, error, formId, Map.of());
        }

        public EditViewState(Map<String, Object> fieldValues, DataSchema schema, boolean isDirty,
                             String listRoute, boolean isCreateMode, Map<String, List<String>> validationErrors) {
            this(fieldValues, schema, isDirty, listRoute,
                    isCreateMode ? FormMode.CREATE : FormMode.EDIT, validationErrors,
                    isCreateMode ? "Create Item" : "Edit Item",
                    isCreateMode ? FormCapabilities.create() : FormCapabilities.edit(),
                    FormStatus.READY, "", false, "data-form");
        }

        public EditViewState(Map<String, Object> fieldValues, DataSchema schema, boolean isDirty,
                             String listRoute, boolean isCreateMode, Map<String, List<String>> validationErrors,
                             String title) {
            this(fieldValues, schema, isDirty, listRoute,
                    isCreateMode ? FormMode.CREATE : FormMode.EDIT, validationErrors, title,
                    isCreateMode ? FormCapabilities.create() : FormCapabilities.edit(),
                    FormStatus.READY, "", false, "data-form");
        }

        public EditViewState(Map<String, Object> fieldValues, DataSchema schema) {
            this(fieldValues, schema, false, "/", FormMode.EDIT, Map.of(), "Edit Item",
                    FormCapabilities.edit(), FormStatus.READY, "", false, "data-form");
        }

        public EditViewState(Map<String, Object> fieldValues, DataSchema schema, boolean isDirty) {
            this(fieldValues, schema, isDirty, "/", FormMode.EDIT, Map.of(), "Edit Item",
                    FormCapabilities.edit(), FormStatus.READY, "", false, "data-form");
        }

        public EditViewState(Map<String, Object> fieldValues, DataSchema schema, boolean isDirty, String listRoute) {
            this(fieldValues, schema, isDirty, listRoute, FormMode.EDIT, Map.of(), "Edit Item",
                    FormCapabilities.edit(), FormStatus.READY, "", false, "data-form");
        }

        public EditViewState(Map<String, Object> fieldValues, DataSchema schema, boolean isDirty,
                             String listRoute, boolean isCreateMode) {
            this(fieldValues, schema, isDirty, listRoute,
                    isCreateMode ? FormMode.CREATE : FormMode.EDIT, Map.of(),
                    isCreateMode ? "Create Item" : "Edit Item",
                    isCreateMode ? FormCapabilities.create() : FormCapabilities.edit(),
                    FormStatus.READY, "", false, "data-form");
        }

        /** Compatibility accessor for the former boolean record component. */
        public boolean isCreateMode() {
            return mode.isCreate();
        }

        public boolean hasErrors() {
            return !validationErrors.isEmpty();
        }

        public List<String> errorsFor(String fieldName) {
            return validationErrors.getOrDefault(fieldName, List.of());
        }

        public List<String> formErrors() {
            return validationErrors.getOrDefault(FormMutationResult.FORM_ERROR, List.of());
        }

        public boolean isBusy() {
            return status.isBusy();
        }

        public EditViewState withMessage(String value, boolean isError) {
            return new EditViewState(fieldValues, schema, isDirty, listRoute, mode, validationErrors,
                    title, capabilities, status, value, isError, formId, choiceSets);
        }

        public EditViewState withStatus(FormStatus value) {
            return new EditViewState(fieldValues, schema, isDirty, listRoute, mode, validationErrors,
                    title, capabilities, value, message, error, formId, choiceSets);
        }

        public ChoiceSet choicesFor(String fieldName) {
            return choiceSets.getOrDefault(fieldName, ChoiceSet.empty());
        }

        private static Map<String, Object> immutableValues(Map<String, Object> source) {
            if (source == null || source.isEmpty()) return Map.of();
            return Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }

        private static Map<String, List<String>> immutableErrors(Map<String, List<String>> source) {
            if (source == null || source.isEmpty()) return Map.of();
            Map<String, List<String>> copied = new LinkedHashMap<>();
            source.forEach((field, errors) -> copied.put(field,
                    errors == null ? List.of() : List.copyOf(errors)));
            return Collections.unmodifiableMap(copied);
        }

        private static Map<String, ChoiceSet> immutableChoiceSets(Map<String, ChoiceSet> source) {
            if (source == null || source.isEmpty()) return Map.of();
            Map<String, ChoiceSet> copied = new LinkedHashMap<>();
            source.forEach((field, choices) -> copied.put(field,
                    choices == null ? ChoiceSet.empty() : choices));
            return Collections.unmodifiableMap(copied);
        }
    }

    /** Resolved choices for one field, including a non-fatal provider error. */
    public record ChoiceSet(List<FieldChoice> choices, String error) {
        public ChoiceSet {
            choices = choices == null ? List.of() : List.copyOf(choices);
            error = error == null ? "" : error;
        }

        public static ChoiceSet empty() {
            return new ChoiceSet(List.of(), "");
        }

        public static ChoiceSet failed(String message) {
            return new ChoiceSet(List.of(), message == null || message.isBlank()
                    ? "Choices could not be loaded." : message);
        }

        public boolean available() {
            return error.isBlank();
        }

        public boolean contains(String value) {
            return choices.stream().anyMatch(choice -> choice.value().equals(value));
        }
    }

    private static Map<String, Object> immutableValues(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
