package rsp.compositions.ui;

import rsp.component.ComponentView;
import rsp.component.IntentDispatcher;
import rsp.compositions.block.FormStatus;
import rsp.compositions.block.FormValueCodec;
import rsp.compositions.schema.FieldDef;
import rsp.compositions.schema.FieldType;
import rsp.compositions.schema.Widget;
import rsp.dsl.Definition;
import rsp.ref.ElementRef;
import rsp.util.json.JsonDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static rsp.compositions.ui.FormField.formField;
import static rsp.compositions.ui.FormField.renderErrors;
import static rsp.dsl.Html.*;

/** Accessible schema-driven create/edit form view for {@link rsp.compositions.block.FormBlock}. */
public class DefaultEditView implements ComponentView<EditView.EditViewState, EditView.EditIntent> {

    @Override
    public rsp.component.View<EditView.EditViewState> resolve(IntentDispatcher<EditView.EditIntent> intents) {
        return state -> {
            List<FieldDef> fields = state.schema().fields();
            Map<String, FieldBinding> bindings = createBindings(fields);
            String titleId = state.formId() + "-title";
            return div(
                    attr("class", "data-form edit-form"),
                    attr("data-form-mode", state.mode().name().toLowerCase(java.util.Locale.ROOT)),
                    state.isBusy() ? attr("aria-busy", "true") : of(),
                    h1(attr("id", titleId), text(state.title())),
                    renderMessage(state, intents),
                    state.status().canRenderFields()
                            ? renderForm(state, fields, bindings, titleId, intents)
                            : renderUnavailableActions(state, intents));
        };
    }

    private Definition renderForm(EditView.EditViewState state,
                                  List<FieldDef> fields,
                                  Map<String, FieldBinding> bindings,
                                  String titleId,
                                  IntentDispatcher<EditView.EditIntent> intents) {
        return form(
                attr("id", state.formId()),
                attr("aria-labelledby", titleId),
                renderErrorSummary(state),
                of(fields.stream().map(field -> renderField(
                        state, field, state.fieldValues().get(field.name()), bindings.get(field.name()),
                        state.errorsFor(field.name()), intents))),
                renderActions(state, intents),
                on("submit", true, context -> dispatchForm(context, fields, bindings, intents)));
    }

    private Definition renderMessage(EditView.EditViewState state,
                                     IntentDispatcher<EditView.EditIntent> intents) {
        if (state.message().isBlank()) return of();
        boolean alert = state.error() || state.status() == FormStatus.NOT_FOUND
                || state.status() == FormStatus.LOAD_FAILED;
        return div(
                attr("class", alert ? "form-message form-message-error" : "form-message form-message-success"),
                attr("role", alert ? "alert" : "status"),
                span(text(state.message())),
                state.status().canRenderFields() && !state.isBusy()
                        ? button(attr("type", "button"), attr("class", "form-message-dismiss"),
                                attr("aria-label", "Dismiss message"), text("×"),
                                on("click", _ -> intents.dispatch(EditView.DismissMessage.INSTANCE)))
                        : of());
    }

    private Definition renderErrorSummary(EditView.EditViewState state) {
        if (!state.hasErrors()) return of();
        List<Definition> entries = new ArrayList<>();
        state.validationErrors().forEach((fieldName, errors) -> {
            FieldDef field = state.schema().field(fieldName);
            String inputId = field == null ? null : errorTargetId(state, field);
            for (String error : errors) {
                entries.add(li(inputId == null ? text(error) : a(attr("href", "#" + inputId), text(error))));
            }
        });
        return div(
                attr("class", "form-error-summary"),
                attr("role", "alert"),
                attr("aria-labelledby", state.formId() + "-error-title"),
                h2(attr("id", state.formId() + "-error-title"), text("Please correct the form")),
                ul(of(entries.stream())));
    }

    private Definition renderField(EditView.EditViewState state,
                                   FieldDef field,
                                   Object currentValue,
                                   FieldBinding binding,
                                   List<String> errors,
                                   IntentDispatcher<EditView.EditIntent> intents) {
        if (field.isHidden()) return of();
        String inputId = inputId(state, field);
        String errorId = inputId + "-errors";
        if (field.widget() == Widget.RADIO) {
            return renderRadioGroup(state, field, currentValue, binding, errors, inputId, errorId, intents);
        }
        Definition input = renderInput(state, field, currentValue, binding.primary(), errors,
                inputId, errorId, intents);
        return formField(field, input, errors, inputId, errorId);
    }

    private Definition renderRadioGroup(EditView.EditViewState state,
                                        FieldDef field,
                                        Object currentValue,
                                        FieldBinding binding,
                                        List<String> errors,
                                        String inputId,
                                        String errorId,
                                        IntentDispatcher<EditView.EditIntent> intents) {
        boolean hasErrors = !errors.isEmpty();
        List<String> options = options(field);
        return fieldset(
                attr("class", "form-field form-radio-group"
                        + (field.isRequired() ? " required" : "")
                        + (hasErrors ? " has-error" : "")),
                hasErrors ? attr("aria-describedby", errorId) : of(),
                legend(text(field.displayName()),
                        field.isRequired() ? span(attr("class", "required-marker"),
                                attr("aria-hidden", "true"), text(" *")) : of(),
                        field.isRequired() ? span(attr("class", "sr-only"), text(" (required)")) : of()),
                of(options.stream().map(option -> {
                    ElementRef optionRef = binding.radioRefs().get(option);
                    String optionId = inputId + "-" + safeId(option);
                    return label(attr("class", "form-radio-option"), attr("for", optionId),
                            input(attr("type", "radio"), attr("id", optionId), attr("name", field.name()),
                                    attr("value", option), ref(optionRef),
                                    option.equals(FormValueCodec.formatForInput(currentValue))
                                            ? attr("checked", "checked") : of(),
                                    field.isRequired() ? attr("required", "required") : of(),
                                    field.isReadOnly() || state.isBusy() ? attr("disabled", "disabled") : of(),
                                    hasErrors ? attr("aria-invalid", "true") : of(),
                                    hasErrors ? attr("aria-describedby", errorId) : of(),
                                    on("change", _ -> intents.dispatch(new EditView.FieldChanged(field.name(), option)))),
                            text(option));
                })),
                renderErrors(errors, errorId));
    }

    private Definition renderInput(EditView.EditViewState state,
                                   FieldDef field,
                                   Object currentValue,
                                   ElementRef fieldRef,
                                   List<String> errors,
                                   String inputId,
                                   String errorId,
                                   IntentDispatcher<EditView.EditIntent> intents) {
        String value = FormValueCodec.formatForInput(currentValue);
        Map<String, String> validation = field.htmlValidationAttributes();
        boolean disabled = field.isReadOnly() || state.isBusy();
        Definition common = of(
                attr("id", inputId), attr("name", field.name()), ref(fieldRef),
                disabled ? attr(field.widget() == Widget.CHECKBOX || field.widget() == Widget.SELECT
                        ? "disabled" : "readonly", disabled ? "disabled" : "readonly") : of(),
                errors.isEmpty() ? of() : attr("aria-invalid", "true"),
                errors.isEmpty() ? of() : attr("aria-describedby", errorId));
        Definition changed = on("change", context -> readRawProperty(context, fieldRef,
                field.widget() == Widget.CHECKBOX ? "checked" : "value")
                .thenAccept(raw -> intents.dispatch(new EditView.FieldChanged(field.name(), raw))));

        return switch (field.widget()) {
            case CHECKBOX -> input(
                    attr("type", "checkbox"), common,
                    Boolean.TRUE.equals(currentValue) ? attr("checked", "checked") : of(),
                    renderValidationAttrs(validation, null), changed);
            case TEXTAREA -> textarea(
                    common, placeholder(field), renderValidationAttrs(validation, null),
                    attr("rows", field.fieldType() == FieldType.TEXT ? "8" : "4"), text(value), changed);
            case SELECT -> select(
                    common, renderValidationAttrs(validation, null),
                    !field.isRequired() ? option(attr("value", ""), text("-- Select --")) : of(),
                    of(options(field).stream().map(option -> option(
                            attr("value", option), option.equals(value) ? attr("selected", "selected") : of(),
                            text(option)))), changed);
            case PASSWORD -> input(
                    attr("type", "password"), common, placeholder(field),
                    attr("autocomplete", state.isCreateMode() ? "new-password" : "current-password"),
                    renderValidationAttrs(validation, "type"), changed);
            case DATE_PICKER -> input(
                    attr("type", field.fieldType() == FieldType.DATETIME ? "datetime-local" : "date"),
                    common, prop("value", value), renderValidationAttrs(validation, "type"), changed);
            case NUMBER -> input(
                    attr("type", "number"), common, prop("value", value), placeholder(field),
                    attr("step", field.fieldType() == FieldType.INTEGER ? "1" : "any"),
                    renderValidationAttrs(validation, "type"), changed);
            case HIDDEN -> of();
            default -> input(
                    attr("type", validation.getOrDefault("type", "text")), common, prop("value", value),
                    placeholder(field), renderValidationAttrs(validation, "type"), changed);
        };
    }

    private Definition renderActions(EditView.EditViewState state,
                                     IntentDispatcher<EditView.EditIntent> intents) {
        return div(attr("class", "form-actions"),
                state.capabilities().canSave()
                        ? button(attr("type", "submit"), attr("class", "save-button"),
                                state.isBusy() ? attr("disabled", "disabled") : of(),
                                text(state.status() == FormStatus.SUBMITTING ? "Saving…" : "Save"))
                        : of(),
                state.capabilities().canCancel() ? renderCancelButton(state, intents) : of(),
                state.capabilities().canDelete()
                        ? button(attr("type", "button"), attr("class", "btn-delete btn-danger"),
                                state.isBusy() ? attr("disabled", "disabled") : of(), text("Delete"),
                                on("click", context -> context.evalJs(
                                                "confirm('Are you sure you want to delete this item?')")
                                        .thenAccept(result -> {
                                            if (result instanceof JsonDataType.Boolean confirmed && confirmed.value()) {
                                                intents.dispatch(EditView.DeleteConfirmed.INSTANCE);
                                            }
                                        })))
                        : of());
    }

    private Definition renderUnavailableActions(EditView.EditViewState state,
                                                IntentDispatcher<EditView.EditIntent> intents) {
        return state.capabilities().canCancel()
                ? div(attr("class", "form-actions"), renderCancelButton(state, intents))
                : of();
    }

    private Definition renderCancelButton(EditView.EditViewState state,
                                          IntentDispatcher<EditView.EditIntent> intents) {
        return button(attr("type", "button"), attr("class", "cancel-button"),
                state.isBusy() ? attr("disabled", "disabled") : of(), text("Cancel"),
                on("click", context -> {
                    if (!state.isDirty()) {
                        intents.dispatch(EditView.CancelRequested.INSTANCE);
                        return;
                    }
                    context.evalJs("confirm('Discard your unsaved changes?')").thenAccept(result -> {
                        if (result instanceof JsonDataType.Boolean confirmed && confirmed.value()) {
                            intents.dispatch(EditView.CancelRequested.INSTANCE);
                        }
                    });
                }));
    }

    private void dispatchForm(rsp.page.EventContext context,
                              List<FieldDef> fields,
                              Map<String, FieldBinding> bindings,
                              IntentDispatcher<EditView.EditIntent> intents) {
        Map<String, CompletableFuture<Object>> futures = new LinkedHashMap<>();
        for (FieldDef field : fields) {
            if (field.isHidden() || field.isReadOnly()) continue;
            FieldBinding binding = bindings.get(field.name());
            futures.put(field.name(), field.widget() == Widget.RADIO
                    ? readRadioValue(context, binding)
                    : readRawProperty(context, binding.primary(),
                            field.widget() == Widget.CHECKBOX ? "checked" : "value"));
        }
        CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).thenRun(() -> {
            Map<String, Object> values = new LinkedHashMap<>();
            futures.forEach((name, future) -> values.put(name, future.join()));
            intents.dispatch(new EditView.FormValuesCollected(values));
        });
    }

    private CompletableFuture<Object> readRadioValue(rsp.page.EventContext context, FieldBinding binding) {
        Map<String, CompletableFuture<Object>> checks = new LinkedHashMap<>();
        binding.radioRefs().forEach((option, ref) -> checks.put(option, readRawProperty(context, ref, "checked")));
        return CompletableFuture.allOf(checks.values().toArray(CompletableFuture[]::new)).thenApply(_ ->
                checks.entrySet().stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getValue().join()))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(""));
    }

    private CompletableFuture<Object> readRawProperty(rsp.page.EventContext context,
                                                       ElementRef ref,
                                                       String property) {
        return context.propertiesByRef(ref).get(property)
                .handle((json, error) -> error == null ? rawJsonValue(json) : FormValueCodec.Unavailable.INSTANCE);
    }

    private Object rawJsonValue(JsonDataType json) {
        if (json instanceof JsonDataType.String string) return string.value();
        if (json instanceof JsonDataType.Boolean bool) return bool.value();
        return json == null ? FormValueCodec.Unavailable.INSTANCE : json.toString();
    }

    private Map<String, FieldBinding> createBindings(List<FieldDef> fields) {
        Map<String, FieldBinding> bindings = new LinkedHashMap<>();
        for (FieldDef field : fields) {
            Map<String, ElementRef> radios = new LinkedHashMap<>();
            if (field.widget() == Widget.RADIO) {
                options(field).forEach(option -> radios.put(option, createElementRef()));
            }
            bindings.put(field.name(), new FieldBinding(createElementRef(), radios));
        }
        return bindings;
    }

    private List<String> options(FieldDef field) {
        if (!field.options().enumOptions().isEmpty()) return field.options().enumOptions();
        if (field.type().isEnum()) {
            return java.util.Arrays.stream(field.type().getEnumConstants())
                    .map(value -> ((Enum<?>) value).name())
                    .toList();
        }
        return List.of();
    }

    private Definition renderValidationAttrs(Map<String, String> attrs, String excluded) {
        return of(attrs.entrySet().stream()
                .filter(entry -> excluded == null || !excluded.equals(entry.getKey()))
                .map(entry -> attr(entry.getKey(), entry.getValue())));
    }

    private Definition placeholder(FieldDef field) {
        return field.options().placeholder() == null
                ? of()
                : attr("placeholder", field.options().placeholder());
    }

    private String inputId(EditView.EditViewState state, FieldDef field) {
        return state.formId() + "-" + safeId(field.name());
    }

    private String errorTargetId(EditView.EditViewState state, FieldDef field) {
        String inputId = inputId(state, field);
        List<String> options = options(field);
        return field.widget() == Widget.RADIO && !options.isEmpty()
                ? inputId + "-" + safeId(options.getFirst())
                : inputId;
    }

    private String safeId(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private record FieldBinding(ElementRef primary, Map<String, ElementRef> radioRefs) {
        private FieldBinding {
            radioRefs = Map.copyOf(radioRefs);
        }
    }
}
