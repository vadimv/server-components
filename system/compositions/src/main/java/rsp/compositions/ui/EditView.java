package rsp.compositions.ui;

import rsp.compositions.schema.DataSchema;

import java.util.List;
import java.util.Map;

/** State and intents shared by form contract components and edit views. */
public final class EditView {
    private EditView() {
    }

    public sealed interface EditIntent permits FormValuesCollected, CancelRequested, DeleteConfirmed {
    }

    public record FormValuesCollected(Map<String, Object> values) implements EditIntent {
        public FormValuesCollected {
            values = Map.copyOf(values);
        }
    }

    public enum CancelRequested implements EditIntent {
        INSTANCE
    }

    public enum DeleteConfirmed implements EditIntent {
        INSTANCE
    }

    public record EditViewState(Map<String, Object> fieldValues,
                                DataSchema schema,
                                boolean isDirty,
                                String listRoute,
                                boolean isCreateMode,
                                Map<String, List<String>> validationErrors,
                                String title) {
        public EditViewState {
            fieldValues = fieldValues == null ? Map.of() : Map.copyOf(fieldValues);
            schema = schema == null ? new DataSchema(List.of()) : schema;
            listRoute = listRoute == null ? "/" : listRoute;
            validationErrors = validationErrors == null ? Map.of() : Map.copyOf(validationErrors);
            title = title == null ? (isCreateMode ? "Create Item" : "Edit Item") : title;
        }

        public EditViewState(Map<String, Object> fieldValues, DataSchema schema, boolean isDirty,
                             String listRoute, boolean isCreateMode, Map<String, List<String>> validationErrors) {
            this(fieldValues, schema, isDirty, listRoute, isCreateMode, validationErrors,
                    isCreateMode ? "Create Item" : "Edit Item");
        }

        public EditViewState(Map<String, Object> fieldValues, DataSchema schema) {
            this(fieldValues, schema, false, "/", false, Map.of(), "Edit Item");
        }

        public EditViewState(Map<String, Object> fieldValues, DataSchema schema, boolean isDirty) {
            this(fieldValues, schema, isDirty, "/", false, Map.of(), "Edit Item");
        }

        public EditViewState(Map<String, Object> fieldValues, DataSchema schema, boolean isDirty, String listRoute) {
            this(fieldValues, schema, isDirty, listRoute, false, Map.of(), "Edit Item");
        }

        public EditViewState(Map<String, Object> fieldValues, DataSchema schema, boolean isDirty,
                             String listRoute, boolean isCreateMode) {
            this(fieldValues, schema, isDirty, listRoute, isCreateMode, Map.of(),
                    isCreateMode ? "Create Item" : "Edit Item");
        }

        public boolean hasErrors() {
            return !validationErrors.isEmpty();
        }

        public List<String> errorsFor(String fieldName) {
            return validationErrors.getOrDefault(fieldName, List.of());
        }
    }
}
