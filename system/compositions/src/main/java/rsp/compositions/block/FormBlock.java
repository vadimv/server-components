package rsp.compositions.block;

import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.Lookup;
import rsp.component.StateUpdater;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.schema.FieldDef;
import rsp.compositions.schema.ValidationResult;
import rsp.compositions.schema.Widget;
import rsp.compositions.ui.EditView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Intent-driven base for schema-driven create and edit forms.
 *
 * <p>The block owns the immutable draft, conversion, validation, operation
 * capabilities, persistence feedback, agent field updates, and navigation
 * effects. Views only render state and dispatch typed intents.</p>
 *
 * @param <T> entity type edited by the form
 */
public abstract class FormBlock<T>
        extends Block<EditView.EditViewState, EditView.EditIntent> {

    private final ComponentView<EditView.EditViewState, EditView.EditIntent> view;
    private final AtomicBoolean mutationInFlight = new AtomicBoolean();
    private volatile EditView.EditViewState currentState;

    protected FormBlock(ComponentView<EditView.EditViewState, EditView.EditIntent> view) {
        this.view = java.util.Objects.requireNonNull(view, "view");
    }

    /** Return the stable schema used for initialization, rendering, validation, and agent payloads. */
    public abstract DataSchema schema();

    /** Named extension point matching {@code ListBlock.listSchema()}. */
    protected DataSchema formSchema() {
        return java.util.Objects.requireNonNull(schema(), "schema");
    }

    protected abstract boolean isCreateMode();

    protected FormMode formMode() {
        return isCreateMode() ? FormMode.CREATE : FormMode.EDIT;
    }

    protected T item(Lookup lookup) {
        return null;
    }

    /** Compatibility persistence hook. Prefer overriding {@link #saveResult(Map)}. */
    public abstract boolean save(Map<String, Object> fieldValues);

    /** Persist a validated, normalized draft and return a renderable outcome. */
    protected FormMutationResult saveResult(Map<String, Object> fieldValues) {
        return save(fieldValues)
                ? FormMutationResult.saved("", "Saved successfully.")
                : FormMutationResult.failure("The item could not be saved.");
    }

    /** Add schema-independent or cross-field validation. */
    protected ValidationResult validate(Map<String, Object> fieldValues) {
        return formSchema().validate(fieldValues);
    }

    protected boolean canSave() {
        return true;
    }

    protected boolean canDelete() {
        return false;
    }

    protected boolean canCancel() {
        return true;
    }

    protected FormCapabilities formCapabilities() {
        return new FormCapabilities(canSave(), canDelete(), canCancel());
    }

    @Override
    public final ComponentStateSupplier<EditView.EditViewState> initStateSupplier() {
        return (_, context) -> initialState(context);
    }

    @Override
    public final ComponentView<EditView.EditViewState, EditView.EditIntent> componentView() {
        return view;
    }

    @Override
    protected void onBlockMounted(EditView.EditViewState state,
                                  StateUpdater<EditView.EditViewState> stateUpdate) {
        remember(state);
        subscribe(FormBlockEvents.FORM_FIELD_SET, (_, payload) -> {
            if (payload == null || !(payload.get("name") instanceof String fieldName)
                    || fieldName.isBlank()) {
                return;
            }
            stateUpdate.applyStateTransformation(current -> updateField(current, fieldName, payload.get("value")));
        });
        subscribe(FormBlockEvents.FORM_SUBMITTED, (_, values) -> submit(values, stateUpdate));
        subscribe(FormBlockEvents.CANCEL_REQUESTED,
                () -> stateUpdate.applyStateTransformation(this::cancel));
    }

    @Override
    protected void onIntent(EditView.EditIntent intent,
                            EditView.EditViewState state,
                            StateUpdater<EditView.EditViewState> stateUpdater) {
        if (intent instanceof EditView.FormValuesCollected formValues) {
            submit(formValues.values(), stateUpdater);
        } else if (intent instanceof EditView.FieldChanged fieldChanged) {
            stateUpdater.setState(updateField(state, fieldChanged.fieldName(), fieldChanged.value()));
        } else if (intent == EditView.CancelRequested.INSTANCE) {
            EditView.EditViewState next = cancel(state);
            if (next != state) stateUpdater.setState(next);
        } else if (intent == EditView.DismissMessage.INSTANCE) {
            stateUpdater.setState(remember(state.withMessage("", false)));
        }
    }

    @Override
    public List<BlockAction> agentActions() {
        List<BlockAction> actions = new ArrayList<>();
        FormCapabilities capabilities = currentCapabilities();
        if (capabilities.canSave()) {
            actions.add(new BlockAction("set_field", FormBlockEvents.FORM_FIELD_SET,
                    "Set one editable form field without submitting so the user can review the draft.",
                    new PayloadSchema.ObjectValue(List.of(
                            new PayloadSchema.Property("name", "string", true,
                                    "Name of an editable field in this form"),
                            new PayloadSchema.Property("value", "string", true,
                                    "New value, converted to the field's declared Java type")))));
            actions.add(new BlockAction("save", FormBlockEvents.FORM_SUBMITTED,
                    "Validate and submit the form", PayloadSchemas.fromDataSchema(formSchema()),
                    DispatchEffect.SCENE_CHANGE));
        }
        if (capabilities.canCancel()) {
            actions.add(new BlockAction("cancel", FormBlockEvents.CANCEL_REQUESTED,
                    "Cancel and return without saving", DispatchEffect.SCENE_CHANGE));
        }
        return List.copyOf(actions);
    }

    @Override
    public BlockMetadata blockMetadata() {
        DataSchema schema = formSchema();
        Map<String, Object> state = new LinkedHashMap<>();
        EditView.EditViewState mounted = currentState;
        FormMode mode = mounted == null ? formMode() : mounted.mode();
        FormCapabilities capabilities = mounted == null ? formCapabilities() : mounted.capabilities();
        state.put("mode", mode.name().toLowerCase(java.util.Locale.ROOT));
        state.put("capabilities", capabilities);
        if (mounted != null) {
            state.put("status", mounted.status().name().toLowerCase(java.util.Locale.ROOT));
            state.put("dirty", mounted.isDirty());
            state.put("draft", valuesForMetadata(schema, mounted.fieldValues()));
            state.put("validationErrors", mounted.validationErrors());
            if (!mounted.message().isBlank()) state.put("message", mounted.message());
        }
        try {
            T entity = item(lookup());
            if (entity != null) state.put("entity", valuesForMetadata(schema, schema.toMap(entity)));
        } catch (RuntimeException exception) {
            state.put("loadError", safeMessage(exception, "The item could not be loaded."));
        }
        return new BlockMetadata(title(),
                formMode().isCreate() ? "Form for creating a new entity" : "Form for editing an existing entity",
                schema, state);
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
        lookup().publish(EventKeys.ACTION_SUCCESS, new EventKeys.ActionResult(blockKey()));
    }

    protected final boolean beginMutation() {
        return mutationInFlight.compareAndSet(false, true);
    }

    protected final void endMutation() {
        mutationInFlight.set(false);
    }

    protected final EditView.EditViewState stateForResult(EditView.EditViewState state,
                                                           FormMutationResult result,
                                                           String defaultFailureMessage) {
        return switch (result.status()) {
            case SUCCESS -> state;
            case INVALID -> copyState(state, state.fieldValues(), true, FormStatus.READY,
                    result.fieldErrors(), messageOr(result.message(), "Please correct the highlighted fields."), true);
            case NOT_FOUND -> copyState(state, state.fieldValues(), state.isDirty(),
                    new FormCapabilities(false, false, state.capabilities().canCancel()), FormStatus.NOT_FOUND,
                    result.fieldErrors(), messageOr(result.message(), "The item no longer exists."), true);
            case CONFLICT -> copyState(state, state.fieldValues(), true, FormStatus.CONFLICT,
                    result.fieldErrors(), messageOr(result.message(), "The item was changed by another operation."), true);
            case FAILURE -> copyState(state, state.fieldValues(), state.isDirty(), FormStatus.FAILED,
                    result.fieldErrors(), messageOr(result.message(), defaultFailureMessage), true);
        };
    }

    protected final EditView.EditViewState copyState(EditView.EditViewState state,
                                                      Map<String, Object> values,
                                                      boolean dirty,
                                                      FormStatus status,
                                                      Map<String, List<String>> errors,
                                                      String message,
                                                      boolean error) {
        return copyState(state, values, dirty, state.capabilities(), status, errors, message, error);
    }

    protected final EditView.EditViewState copyState(EditView.EditViewState state,
                                                      Map<String, Object> values,
                                                      boolean dirty,
                                                      FormCapabilities capabilities,
                                                      FormStatus status,
                                                      Map<String, List<String>> errors,
                                                      String message,
                                                      boolean error) {
        return remember(new EditView.EditViewState(values, state.schema(), dirty, state.listRoute(), state.mode(),
                errors, state.title(), capabilities, status, message, error, state.formId()));
    }

    /** Effective capabilities of the mounted form, or configured defaults before mounting. */
    protected final FormCapabilities currentCapabilities() {
        EditView.EditViewState mounted = currentState;
        return mounted == null ? formCapabilities() : mounted.capabilities();
    }

    private EditView.EditViewState initialState(ComponentContext context) {
        Lookup initialLookup = LookupFactory.create(context);
        DataSchema schema = formSchema();
        FormMode mode = formMode();
        FormCapabilities capabilities = formCapabilities();
        String route = listRoute(initialLookup);
        String formId = "form-" + Integer.toUnsignedString(System.identityHashCode(this));
        try {
            T entity = item(initialLookup);
            if (!mode.isCreate() && entity == null) {
                return remember(new EditView.EditViewState(emptyFieldValues(schema), schema, false, route, mode, Map.of(),
                        title(), new FormCapabilities(false, false, capabilities.canCancel()), FormStatus.NOT_FOUND,
                        "The requested item was not found.", true, formId));
            }
            Map<String, Object> values = entity == null
                    ? emptyFieldValues(schema)
                    : valuesForSchema(schema, schema.toMap(entity));
            return remember(new EditView.EditViewState(values, schema, false, route, mode, Map.of(), title(),
                    capabilities, FormStatus.READY, "", false, formId));
        } catch (RuntimeException exception) {
            return remember(new EditView.EditViewState(emptyFieldValues(schema), schema, false, route, mode, Map.of(), title(),
                    new FormCapabilities(false, false, capabilities.canCancel()), FormStatus.LOAD_FAILED,
                    safeMessage(exception, "The item could not be loaded."), true, formId));
        }
    }

    private void submit(Map<String, Object> submitted,
                        StateUpdater<EditView.EditViewState> stateUpdater) {
        stateUpdater.applyStateTransformation(current -> {
            if (!beginMutation()) return current;
            try {
                if (!current.capabilities().canSave() || current.isBusy()
                        || !current.status().canRenderFields()) {
                    return current;
                }
                EditView.EditViewState submitting = copyState(current, current.fieldValues(), current.isDirty(),
                        FormStatus.SUBMITTING, current.validationErrors(), "Saving…", false);
                try {
                    return submitCurrent(submitting, submitted == null ? Map.of() : submitted);
                } catch (RuntimeException exception) {
                    return copyState(submitting, submitting.fieldValues(), true, FormStatus.FAILED, Map.of(),
                            safeMessage(exception, "The item could not be saved."), true);
                }
            } finally {
                endMutation();
            }
        });
    }

    private EditView.EditViewState submitCurrent(EditView.EditViewState state,
                                                  Map<String, Object> submitted) {
        NormalizedDraft normalized = normalizeDraft(state, submitted);
        ValidationResult validation = validate(normalized.values());
        Map<String, List<String>> errors = mergeErrors(normalized.errors(), validation.errors());
        if (!errors.isEmpty()) {
            return copyState(state, normalized.values(), true, FormStatus.READY, errors,
                    "Please correct the highlighted fields.", true);
        }

        EditView.EditViewState normalizedState = copyState(state, normalized.values(), true,
                FormStatus.SUBMITTING, Map.of(), "Saving…", false);
        try {
            FormMutationResult result = java.util.Objects.requireNonNull(
                    saveResult(normalized.values()), "saveResult");
            if (result.succeeded()) {
                publishSuccess();
                return copyState(normalizedState, normalized.values(), false, FormStatus.SUBMITTING, Map.of(),
                        messageOr(result.message(), "Saved successfully."), false);
            }
            return stateForResult(normalizedState, result, "The item could not be saved.");
        } catch (RuntimeException exception) {
            return copyState(normalizedState, normalized.values(), true, FormStatus.FAILED, Map.of(),
                    safeMessage(exception, "The item could not be saved."), true);
        }
    }

    private EditView.EditViewState updateField(EditView.EditViewState state,
                                                String fieldName,
                                                Object rawValue) {
        if (!state.capabilities().canSave() || state.isBusy()) return state;
        FieldDef field = state.schema().field(fieldName);
        if (field == null || field.isHidden() || field.isReadOnly()) {
            return remember(state.withMessage("Field '" + fieldName + "' is not editable.", true));
        }
        FormValueCodec.Conversion conversion = FormValueCodec.convert(field, rawValue);
        Map<String, List<String>> errors = new LinkedHashMap<>(state.validationErrors());
        if (!conversion.valid()) {
            errors.put(fieldName, List.of(conversion.error()));
            Map<String, Object> values = new LinkedHashMap<>(state.fieldValues());
            if (rawValue != FormValueCodec.Unavailable.INSTANCE) values.put(fieldName, rawValue);
            return copyState(state, values, true, FormStatus.READY, errors,
                    "Please correct the highlighted field.", true);
        }
        Map<String, Object> values = new LinkedHashMap<>(state.fieldValues());
        values.put(fieldName, conversion.value());
        errors.remove(fieldName);
        return copyState(state, values, true, FormStatus.READY, errors, "", false);
    }

    private EditView.EditViewState cancel(EditView.EditViewState state) {
        if (!state.capabilities().canCancel() || state.isBusy()) return state;
        publishSuccess();
        return state;
    }

    private static NormalizedDraft normalizeDraft(EditView.EditViewState state,
                                                   Map<String, Object> submitted) {
        Map<String, Object> values = new LinkedHashMap<>(state.fieldValues());
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (FieldDef field : state.schema().fields()) {
            if (field.isHidden() || field.isReadOnly() || !submitted.containsKey(field.name())) continue;
            FormValueCodec.Conversion conversion = FormValueCodec.convert(field, submitted.get(field.name()));
            if (conversion.valid()) {
                values.put(field.name(), conversion.value());
            } else {
                if (submitted.get(field.name()) != FormValueCodec.Unavailable.INSTANCE) {
                    values.put(field.name(), submitted.get(field.name()));
                }
                errors.put(field.name(), List.of(conversion.error()));
            }
        }
        return new NormalizedDraft(values, errors);
    }

    private static Map<String, Object> emptyFieldValues(DataSchema schema) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (FieldDef field : schema.fields()) {
            values.put(field.name(), FormValueCodec.initialValue(field));
        }
        return values;
    }

    private static Map<String, Object> valuesForSchema(DataSchema schema, Map<String, Object> source) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (FieldDef field : schema.fields()) {
            values.put(field.name(), source.containsKey(field.name())
                    ? source.get(field.name())
                    : FormValueCodec.initialValue(field));
        }
        return values;
    }

    private static Map<String, Object> valuesForMetadata(DataSchema schema, Map<String, Object> source) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (FieldDef field : schema.fields()) {
            if (field.widget() != Widget.PASSWORD && source.containsKey(field.name())) {
                values.put(field.name(), source.get(field.name()));
            }
        }
        return java.util.Collections.unmodifiableMap(values);
    }

    private EditView.EditViewState remember(EditView.EditViewState state) {
        currentState = state;
        return state;
    }

    private static Map<String, List<String>> mergeErrors(Map<String, List<String>> first,
                                                          Map<String, List<String>> second) {
        Map<String, List<String>> merged = new LinkedHashMap<>();
        first.forEach((field, errors) -> merged.put(field, List.copyOf(errors)));
        second.forEach((field, errors) -> merged.merge(field, List.copyOf(errors), (left, right) -> {
            List<String> combined = new ArrayList<>(left);
            combined.addAll(right);
            return List.copyOf(combined);
        }));
        return merged;
    }

    private static String messageOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String safeMessage(RuntimeException exception, String fallback) {
        return fallback;
    }

    private record NormalizedDraft(Map<String, Object> values, Map<String, List<String>> errors) {
    }
}
