package rsp.compositions.ui;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.EditViewContract;
import rsp.compositions.schema.DataSchema;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

import java.util.List;
import java.util.Map;

/**
 * EditView - Adaptive edit/form view component.
 * <p>
 * Renders forms for editing any domain objects based on schema metadata.
 * UI adapts to any number of fields and types at runtime.
 * <p>
 * Event handling is delegated to the {@link EditViewContract} which registers
 * handlers for form.submitted and delete.requested events.
 */
public abstract class EditView extends Component<EditView.EditViewState> {

    protected Lookup lookup;

    /**
     * State containing the entity data being edited and schema metadata.
     *
     * @param fieldValues Map of field names to current values
     * @param schema Schema defining field metadata
     * @param isDirty Whether form has unsaved changes
     * @param listRoute Route to navigate back to list
     * @param isCreateMode Whether in create mode (new entity) vs edit mode (existing entity)
     * @param validationErrors Map of field names to validation error messages
     */
    public record EditViewState(
        Map<String, Object> fieldValues,
        DataSchema schema,
        boolean isDirty,
        String listRoute,
        boolean isCreateMode,
        Map<String, List<String>> validationErrors
    ) {
        public EditViewState {
            fieldValues = fieldValues != null ? fieldValues : Map.of();
            schema = schema != null ? schema : new DataSchema(java.util.List.of());
            listRoute = listRoute != null ? listRoute : "/";
            validationErrors = validationErrors != null ? validationErrors : Map.of();
        }

        public EditViewState(Map<String, Object> fieldValues, DataSchema schema) {
            this(fieldValues, schema, false, "/", false, Map.of());
        }

        public EditViewState(Map<String, Object> fieldValues, DataSchema schema, boolean isDirty) {
            this(fieldValues, schema, isDirty, "/", false, Map.of());
        }

        public EditViewState(Map<String, Object> fieldValues, DataSchema schema, boolean isDirty, String listRoute) {
            this(fieldValues, schema, isDirty, listRoute, false, Map.of());
        }

        public EditViewState(Map<String, Object> fieldValues, DataSchema schema, boolean isDirty, String listRoute, boolean isCreateMode) {
            this(fieldValues, schema, isDirty, listRoute, isCreateMode, Map.of());
        }

        /**
         * Check if there are any validation errors.
         */
        public boolean hasErrors() {
            return !validationErrors.isEmpty();
        }

        /**
         * Get validation error messages for a specific field.
         *
         * @param fieldName The field name
         * @return List of error messages (empty if no errors)
         */
        public List<String> errorsFor(String fieldName) {
            return validationErrors.getOrDefault(fieldName, List.of());
        }
    }

    @Override
    public ComponentStateSupplier<EditViewState> initStateSupplier() {
        return (_, context) -> {
            // Read entity and schema from context (populated by ServicesComponent via contract.enrichContext())
            Object entity = context.get(ContextKeys.EDIT_ENTITY);
            DataSchema schema = context.get(ContextKeys.EDIT_SCHEMA);
            String listRoute = context.get(ContextKeys.EDIT_LIST_ROUTE);
            Boolean isCreateModeValue = context.get(ContextKeys.EDIT_IS_CREATE_MODE);

            // Apply defaults
            listRoute = listRoute != null ? listRoute : "/";
            boolean isCreateMode = isCreateModeValue != null && isCreateModeValue;

            if (schema == null && entity != null) {
                // Auto-derive schema from entity if not provided
                schema = DataSchema.fromFirstItem(entity);
            }

            if (schema == null) {
                return new EditViewState(Map.of(), new DataSchema(java.util.List.of()), false, listRoute, isCreateMode, Map.of());
            }

            // Convert entity to Map for editing
            Map<String, Object> fieldValues = entity != null
                ? schema.toMap(entity)
                : createEmptyFieldValues(schema);

            return new EditViewState(fieldValues, schema, false, listRoute, isCreateMode, Map.of());
        };
    }

    @Override
    public ComponentSegment<EditViewState> createComponentSegment(final QualifiedSessionId sessionId,
                                                                   final TreePositionPath componentPath,
                                                                   final TreeBuilderFactory treeBuilderFactory,
                                                                   final ComponentContext componentContext,
                                                                   final CommandsEnqueue commandsEnqueue) {
        // Create Lookup for use in view (for event publishing)
        this.lookup = createLookup(componentContext, commandsEnqueue);

        // Create the segment
        return super.createComponentSegment(sessionId, componentPath, treeBuilderFactory, componentContext, commandsEnqueue);
    }

    /**
     * Create a Lookup from ComponentContext for event publishing.
     */
    private Lookup createLookup(ComponentContext context, CommandsEnqueue commandsEnqueue) {
        Subscriber subscriber = context.get(Subscriber.class);
        if (subscriber == null) {
            // Fallback: create a no-op subscriber for publish-only usage
            subscriber = NoOpSubscriber.INSTANCE;
        }
        return new ContextLookup(context, commandsEnqueue, subscriber);
    }

    /**
     * No-op Subscriber for Views that only need to publish events, not subscribe.
     */
    private static final class NoOpSubscriber implements Subscriber {
        static final NoOpSubscriber INSTANCE = new NoOpSubscriber();

        @Override
        public void addWindowEventHandler(String eventType, java.util.function.Consumer<rsp.page.EventContext> eventHandler,
                                          boolean preventDefault, rsp.dom.DomEventEntry.Modifier modifier) {}

        @Override
        public void addComponentEventHandler(String eventType, java.util.function.Consumer<ComponentEventEntry.EventContext> eventHandler,
                                             boolean preventDefault) {}
    }

    /**
     * Create empty field values map initialized with default values based on field types.
     */
    private Map<String, Object> createEmptyFieldValues(DataSchema schema) {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        for (DataSchema.ColumnDef column : schema.columns()) {
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
