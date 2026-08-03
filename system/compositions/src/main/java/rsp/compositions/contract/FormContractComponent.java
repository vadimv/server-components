package rsp.compositions.contract;

import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.Lookup;
import rsp.component.StateUpdater;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.schema.ValidationResult;
import rsp.compositions.ui.EditView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Intent-driven base for create and edit contracts.
 *
 * <p>The component owns form drafts, validation, agent field updates, and
 * persistence effects. The supplied view renders the immutable draft and may
 * only dispatch {@link EditView.EditIntent} values.</p>
 *
 * @param <T> entity type edited by the form
 */
public abstract class FormContractComponent<T>
        extends ContractNodeComponent<EditView.EditViewState, EditView.EditIntent> {

    private final ComponentView<EditView.EditViewState, EditView.EditIntent> view;

    protected FormContractComponent(ComponentView<EditView.EditViewState, EditView.EditIntent> view) {
        this.view = java.util.Objects.requireNonNull(view, "view");
    }

    public abstract DataSchema schema();

    protected abstract boolean isCreateMode();

    protected T item(Lookup lookup) {
        return null;
    }

    public abstract boolean save(Map<String, Object> fieldValues);

    @Override
    public final ComponentStateSupplier<EditView.EditViewState> initStateSupplier() {
        return (_, context) -> initialState(context);
    }

    @Override
    public final ComponentView<EditView.EditViewState, EditView.EditIntent> componentView() {
        return view;
    }

    @Override
    protected void onContractMounted(EditView.EditViewState state,
                                     StateUpdater<EditView.EditViewState> stateUpdate) {
        subscribe(FormContractEvents.FORM_FIELD_SET, (_, payload) -> {
            if (payload == null || !(payload.get("name") instanceof String fieldName) || fieldName.isEmpty()) {
                return;
            }
            Object value = payload.get("value");
            stateUpdate.applyStateTransformation(current -> updateField(current, fieldName,
                    value != null ? value.toString() : ""));
        });

        subscribe(FormContractEvents.FORM_SUBMITTED,
                (_, values) -> submit(values, stateUpdate));
        subscribe(FormContractEvents.CANCEL_REQUESTED,
                () -> publishSuccess());
    }

    @Override
    protected void onIntent(EditView.EditIntent intent,
                            EditView.EditViewState state,
                            StateUpdater<EditView.EditViewState> stateUpdater) {
        if (intent instanceof EditView.FormValuesCollected formValues) {
            submit(formValues.values(), stateUpdater);
        } else if (intent == EditView.CancelRequested.INSTANCE) {
            publishSuccess();
        }
    }

    @Override
    public List<ContractAction> agentActions() {
        return List.of(
                new ContractAction("set_field", FormContractEvents.FORM_FIELD_SET,
                        "Set a single form field value without submitting. Use this to pre-fill the form one field at a time so the user can review.",
                        new PayloadSchema.ObjectValue(List.of(
                                new PayloadSchema.Property("name", "string", true,
                                        "Name of the field to set (must match a field in this form's schema)"),
                                new PayloadSchema.Property("value", "string", true,
                                        "New value for the field (will be coerced to the field's declared type at submit)")))),
                new ContractAction("save", FormContractEvents.FORM_SUBMITTED,
                        "Submit form data", PayloadSchemas.fromDataSchema(schema()), DispatchEffect.SCENE_CHANGE),
                new ContractAction("cancel", FormContractEvents.CANCEL_REQUESTED,
                        "Cancel and go back", DispatchEffect.SCENE_CHANGE));
    }

    @Override
    public ContractMetadata contractMetadata() {
        T entity = item(lookup());
        Map<String, Object> state = entity == null ? Map.of() : Map.of("entity", schema().toMap(entity));
        String description = isCreateMode() ? "Form for creating a new entity" : "Form for editing an existing entity";
        return new ContractMetadata(title(), description, schema(), state);
    }

    protected final String listRoute() {
        return listRoute(lookup());
    }

    protected final String listRoute(Lookup lookup) {
        String routePattern = lookup.get(ContextKeys.ROUTE_PATTERN);
        if (routePattern == null) {
            throw new IllegalStateException("route.pattern not found in context");
        }
        return RouteUtils.buildParentRoute(routePattern, lookup).toString();
    }

    protected final void publishSuccess() {
        lookup().publish(EventKeys.ACTION_SUCCESS, new EventKeys.ActionResult(getClass()));
    }

    private EditView.EditViewState initialState(ComponentContext context) {
        Lookup initialLookup = LookupFactory.create(context);
        DataSchema schema = schema();
        T entity = item(initialLookup);
        Map<String, Object> fieldValues = entity != null ? schema.toMap(entity) : emptyFieldValues(schema);
        return new EditView.EditViewState(fieldValues, schema, false, listRoute(initialLookup),
                isCreateMode(), Map.of(), title());
    }

    private void submit(Map<String, Object> values, StateUpdater<EditView.EditViewState> stateUpdater) {
        ValidationResult result = schema().validate(values);
        if (!result.isValid()) {
            stateUpdater.applyStateTransformation(current -> new EditView.EditViewState(values, current.schema(), true,
                    current.listRoute(), current.isCreateMode(), result.errors(), current.title()));
            return;
        }
        stateUpdater.applyStateTransformation(current -> new EditView.EditViewState(values, current.schema(),
                current.isDirty(), current.listRoute(), current.isCreateMode(), Map.of(), current.title()));
        if (save(values)) {
            publishSuccess();
        }
    }

    private static EditView.EditViewState updateField(EditView.EditViewState state,
                                                       String fieldName,
                                                       String value) {
        if (state.schema().field(fieldName) == null) {
            return state;
        }
        Map<String, Object> values = new LinkedHashMap<>(state.fieldValues());
        values.put(fieldName, value);
        return new EditView.EditViewState(values, state.schema(), true, state.listRoute(),
                state.isCreateMode(), state.validationErrors(), state.title());
    }

    private static Map<String, Object> emptyFieldValues(DataSchema schema) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (DataSchema.ColumnDef column : schema.columns()) {
            values.put(column.name(), defaultValue(column.type()));
        }
        return values;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == String.class) return "";
        if (type == Integer.class || type == int.class) return 0;
        if (type == Long.class || type == long.class) return 0L;
        if (type == Double.class || type == double.class) return 0.0;
        if (type == Boolean.class || type == boolean.class) return false;
        return null;
    }
}
