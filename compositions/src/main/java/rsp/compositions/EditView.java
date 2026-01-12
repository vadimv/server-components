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
 * Event handling is delegated to the {@link EditViewContract} which registers
 * handlers for form.submitted and delete.requested events.
 */
public abstract class EditView extends Component<EditView.EditViewState> {

    protected Consumer<Command> commandsEnqueue;
    protected NavigationContext navigationContext;

    /**
     * State containing the entity data being edited and schema metadata.
     *
     * @param fieldValues Map of field names to current values
     * @param schema Schema defining field metadata
     * @param isDirty Whether form has unsaved changes
     * @param listRoute Route to navigate back to list
     * @param isCreateMode Whether in create mode (new entity) vs edit mode (existing entity)
     */
    public record EditViewState(Map<String, Object> fieldValues, ListSchema schema, boolean isDirty, String listRoute, boolean isCreateMode) {
        public EditViewState {
            fieldValues = fieldValues != null ? fieldValues : Map.of();
            schema = schema != null ? schema : new ListSchema(java.util.List.of());
            listRoute = listRoute != null ? listRoute : "/";
        }

        public EditViewState(Map<String, Object> fieldValues, ListSchema schema) {
            this(fieldValues, schema, false, "/", false);
        }

        public EditViewState(Map<String, Object> fieldValues, ListSchema schema, boolean isDirty) {
            this(fieldValues, schema, isDirty, "/", false);
        }

        public EditViewState(Map<String, Object> fieldValues, ListSchema schema, boolean isDirty, String listRoute) {
            this(fieldValues, schema, isDirty, listRoute, false);
        }
    }

    @Override
    public ComponentStateSupplier<EditViewState> initStateSupplier() {
        return (_, context) -> {
            // Read entity and schema from context (populated by ServicesComponent)
            Object entity = context.get(ContextKeys.EDIT_ENTITY);
            ListSchema schema = context.get(ContextKeys.EDIT_SCHEMA);

            // Read UI hints from context
            // These are populated by the framework based on the contract
            String listRoute = context.get(ContextKeys.EDIT_LIST_ROUTE);
            Boolean isCreateModeValue = context.get(ContextKeys.EDIT_IS_CREATE_MODE);

            // Fallback to contract if context keys not set (backward compatibility)
            if (listRoute == null || isCreateModeValue == null) {
                EditViewContract<?> contract = resolveContract(context);
                if (contract != null) {
                    if (listRoute == null) {
                        listRoute = contract.listRoute();
                    }
                    if (isCreateModeValue == null) {
                        isCreateModeValue = contract.isCreateMode();
                    }
                }
            }

            // Apply defaults
            listRoute = listRoute != null ? listRoute : "/";
            boolean isCreateMode = isCreateModeValue != null && isCreateModeValue;

            if (schema == null && entity != null) {
                // Auto-derive schema from entity if not provided
                schema = ListSchema.fromFirstItem(entity);
            }

            if (schema == null) {
                return new EditViewState(Map.of(), new ListSchema(java.util.List.of()), false, listRoute, isCreateMode);
            }

            // Convert entity to Map for editing
            Map<String, Object> fieldValues = entity != null
                ? schema.toMap(entity)
                : createEmptyFieldValues(schema);

            return new EditViewState(fieldValues, schema, false, listRoute, isCreateMode);
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

        // Initialize NavigationContext
        this.navigationContext = new NavigationContext(componentContext);

        // Create the segment
        ComponentSegment<EditViewState> segment = super.createComponentSegment(sessionId, componentPath, treeBuilderFactory, componentContext, commandsEnqueue);

        // Delegate event handling to the contract
        // Contract registers handlers for form.submitted and delete.requested events
        EditViewContract<?> contract = resolveContract(componentContext);
        if (contract != null) {
            boolean isModalMode = componentContext.get(ContextKeys.MODAL_OVERLAY_VIEW_CONTRACT) != null;
            contract.registerHandlers(segment, commandsEnqueue, navigationContext, isModalMode);
        }

        return segment;
    }

    /**
     * Resolve the EditViewContract from context.
     * <p>
     * Checks in order: MODAL overlay, QUERY_PARAM overlay, then primary view contract.
     *
     * @param componentContext The component context
     * @return The resolved contract, or null if not found
     */
    private EditViewContract<?> resolveContract(ComponentContext componentContext) {
        // Check MODAL overlay first
        EditViewContract<?> contract = (EditViewContract<?>) componentContext.get(ContextKeys.MODAL_OVERLAY_VIEW_CONTRACT);
        if (contract != null) {
            return contract;
        }

        // Check QUERY_PARAM overlay
        contract = (EditViewContract<?>) componentContext.get(ContextKeys.OVERLAY_VIEW_CONTRACT);
        if (contract != null) {
            return contract;
        }

        // Check primary view contract
        ViewContract viewContract = componentContext.get(ContextKeys.VIEW_CONTRACT);
        if (viewContract instanceof EditViewContract<?> editContract) {
            return editContract;
        }

        return null;
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
