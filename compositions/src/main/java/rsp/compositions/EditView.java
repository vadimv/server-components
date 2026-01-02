package rsp.compositions;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;

import java.util.Map;
import java.util.function.Consumer;

/**
 * EditView - Adaptive edit/form view component.
 * <p>
 * Renders forms for editing any domain objects based on schema metadata.
 * UI adapts to any number of fields and types at runtime.
 * <p>
 * Sends action notifications when user clicks action buttons (save, cancel, etc.)
 */
public abstract class EditView extends Component<EditView.EditViewState> {

    protected Consumer<Command> commandsEnqueue;

    /**
     * State containing the entity data being edited and schema metadata.
     */
    public record EditViewState(Map<String, Object> fieldValues, ListSchema schema, boolean isDirty) {
        public EditViewState {
            fieldValues = fieldValues != null ? fieldValues : Map.of();
            schema = schema != null ? schema : new ListSchema(java.util.List.of());
        }

        public EditViewState(Map<String, Object> fieldValues, ListSchema schema) {
            this(fieldValues, schema, false);
        }
    }

    @Override
    public ComponentStateSupplier<EditViewState> initStateSupplier() {
        return (_, context) -> {
            // Read entity and schema from context (populated by ServicesComponent)
            Object entity = context.getAttribute("edit.entity");
            ListSchema schema = (ListSchema) context.getAttribute("edit.schema");

            if (schema == null && entity != null) {
                // Auto-derive schema from entity if not provided
                schema = ListSchema.fromFirstItem(entity);
            }

            if (schema == null) {
                return new EditViewState(Map.of(), new ListSchema(java.util.List.of()));
            }

            // Convert entity to Map for editing
            Map<String, Object> fieldValues = entity != null
                ? schema.toMap(entity)
                : createEmptyFieldValues(schema);

            return new EditViewState(fieldValues, schema, false);
        };
    }

    @Override
    public ComponentSegment<EditViewState> createComponentSegment(final QualifiedSessionId sessionId,
                                                                   final TreePositionPath componentPath,
                                                                   final TreeBuilderFactory treeBuilderFactory,
                                                                   final ComponentContext componentContext,
                                                                   final Consumer<Command> commandsEnqueue) {
        // Store commandsEnqueue for use in view
        this.commandsEnqueue = commandsEnqueue;
        return super.createComponentSegment(sessionId, componentPath, treeBuilderFactory, componentContext, commandsEnqueue);
    }

    /**
     * Create empty field values map initialized with default values based on field types.
     */
    private Map<String, Object> createEmptyFieldValues(ListSchema schema) {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        for (ListSchema.ColumnDef column : schema.columns()) {
            values.put(column.name(), getDefaultValue(column.type()));
        }
        return values;
    }

    /**
     * Get default value for a field type.
     */
    private Object getDefaultValue(Class<?> type) {
        if (type == String.class) return "";
        if (type == Integer.class || type == int.class) return 0;
        if (type == Long.class || type == long.class) return 0L;
        if (type == Double.class || type == double.class) return 0.0;
        if (type == Boolean.class || type == boolean.class) return false;
        return null;
    }
}
