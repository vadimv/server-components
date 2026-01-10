package rsp.compositions;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;
import rsp.page.events.ComponentEventNotification;
import rsp.page.events.RemoteCommand;

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

            // Get the contract to derive listRoute and create mode
            // Check in order: MODAL overlay, QUERY_PARAM overlay, then primary view contract
            EditViewContract<?> contract = (EditViewContract<?>) context.get(ContextKeys.MODAL_OVERLAY_VIEW_CONTRACT);
            if (contract == null) {
                contract = (EditViewContract<?>) context.get(ContextKeys.OVERLAY_VIEW_CONTRACT);
            }
            if (contract == null) {
                ViewContract viewContract = context.get(ContextKeys.VIEW_CONTRACT);
                if (viewContract instanceof EditViewContract<?> editContract) {
                    contract = editContract;
                }
            }
            String listRoute = contract != null ? contract.listRoute() : "/";
            boolean isCreateMode = contract != null && contract.isCreateMode();

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

        // Register handler for save action
        segment.addComponentEventHandler("action.save", eventContext -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> fieldValues = (Map<String, Object>) eventContext.eventObject();

            // Get the contract from context
            // Check in order: MODAL overlay, QUERY_PARAM overlay, then primary view contract
            EditViewContract<?> contract = (EditViewContract<?>) componentContext.get(ContextKeys.MODAL_OVERLAY_VIEW_CONTRACT);
            boolean isModalMode = contract != null;

            if (contract == null) {
                contract = (EditViewContract<?>) componentContext.get(ContextKeys.OVERLAY_VIEW_CONTRACT);
            }
            if (contract == null) {
                ViewContract viewContract = componentContext.get(ContextKeys.VIEW_CONTRACT);
                if (viewContract instanceof EditViewContract<?> editContract) {
                    contract = editContract;
                }
            }

            if (contract != null) {
                boolean success = contract.save(fieldValues);
                if (success) {
                    if (isModalMode) {
                        // MODAL mode: emit modalSaveSuccess to close modal and refresh list
                        commandsEnqueue.accept(new ComponentEventNotification("modalSaveSuccess", Map.of()));
                    } else {
                        // QUERY_PARAM or SEPARATE_PAGE: navigate back to list
                        commandsEnqueue.accept(navigationContext.navigateToList());
                    }
                }
            }
        }, false);

        return segment;
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
