package rsp.compositions.contract;

import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.agent.AgentAction;
import rsp.compositions.agent.ContractMetadata;
import rsp.compositions.agent.PayloadParsers;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.schema.ValidationResult;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static rsp.compositions.contract.EventKeys.ACTION_SUCCESS;

/**
 * FormViewContract - Base contract for form-based views (create and edit).
 * <p>
 * Provides shared functionality for form handling:
 * <ul>
 *   <li>Schema definition for form fields</li>
 *   <li>Save operation with validation</li>
 *   <li>Navigation back to list view</li>
 *   <li>Form submission event handling</li>
 * </ul>
 * <p>
 * Subclasses:
 * <ul>
 *   <li>{@link CreateViewContract} - For creating new entities</li>
 *   <li>{@link EditViewContract} - For editing existing entities</li>
 * </ul>
 * <p>
 * <b>Placement-agnostic:</b> Contracts don't store placement state (isModalMode, isActiveOverlay).
 * Instead, they query the scene via SlotUtils to determine their slot when making decisions.
 *
 * @param <T> The type of entity being created or edited
 */
public abstract class FormViewContract<T> extends ViewContract {

    public static final EventKey.VoidKey CANCEL_REQUESTED =
            new EventKey.VoidKey("cancel.requested");
    /**
     * Form submitted with field values.
     * Emitted by: DefaultEditView
     * Handled by: EditViewContract.registerHandlers()
     */
    @SuppressWarnings("unchecked")
    public static final EventKey.SimpleKey<Map<String, Object>> FORM_SUBMITTED =
            new EventKey.SimpleKey<>("form.submitted",
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

    protected FormViewContract(final Lookup lookup) {
        super(lookup);

        // Handle form submission - check if active using context
        subscribe(FORM_SUBMITTED, (eventName, fieldValues) -> {
            if (shouldHandleEvent()) {
                handleFormSubmitted(fieldValues);
            }
        });

        // Handle cancel request
        subscribe(CANCEL_REQUESTED, () -> {
            lookup.publish(ACTION_SUCCESS,
                    new EventKeys.ActionResult(this.getClass()));
        });
    }

    /**
     * Check if this contract should handle events.
     * <p>
     * Generic - reads from context, no local state.
     * When multiple overlays are stacked, only the topmost has IS_ACTIVE_CONTRACT=true.
     *
     * @return true if this contract should handle events
     */
    protected boolean shouldHandleEvent() {
        Boolean isActive = lookup.get(ContextKeys.IS_ACTIVE_CONTRACT);
        return isActive == null || isActive;  // Default true if not set
    }

    /**
     * Get the schema for the entity's fields.
     * <p>
     * Defines form field configuration including types, validation, and UI hints.
     *
     * @return Schema metadata for rendering form fields
     */
    public abstract DataSchema schema();

    /**
     * Save the entity with the given field values.
     * <p>
     * Called when the user submits the form.
     *
     * @param fieldValues Map of field names to values from the form
     * @return true if save succeeded, false otherwise
     */
    public abstract boolean save(Map<String, Object> fieldValues);

    /**
     * Get the list route to navigate back to after save/cancel.
     * <p>
     * Uses generic RouteUtils - no URL logic in contract.
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code "/posts/:id" → "/posts"}</li>
     *   <li>{@code "/posts/new" → "/posts"}</li>
     *   <li>{@code "/posts/:id" with fromP=3 → "/posts?p=3"}</li>
     * </ul>
     *
     * @return The list route path with restored query parameters
     */
    public String listRoute() {
        String routePattern = lookup.get(ContextKeys.ROUTE_PATTERN);
        if (routePattern == null) {
            throw new IllegalStateException("route.pattern not found in context");
        }
        return RouteUtils.buildParentRoute(routePattern, lookup);
    }

    // ========================================================================
    // Event Handling - Simplified, uses generic utilities
    // ========================================================================

    /**
     * Handle form submission event.
     * <p>
     * Validates field values, then calls {@link #save(Map)} on success.
     *
     * @param fieldValues The submitted field values
     */
    protected void handleFormSubmitted(Map<String, Object> fieldValues) {
        // Validate before saving
        ValidationResult result = schema().validate(fieldValues);
        if (!result.isValid()) {
            onValidationFailed(result);
            return;
        }

        boolean success = save(fieldValues);
        if (success) {
            onSaveSuccess();
        } else {
            onSaveFailure();
        }
    }

    /**
     * Called when validation fails.
     * <p>
     * Default: Does nothing (form stays on page). Override to show validation errors.
     *
     * @param result The validation result containing field errors
     */
    protected void onValidationFailed(ValidationResult result) {
        // Default: stay on page
    }

    /**
     * Called when save succeeds.
     * <p>
     * Emits ACTION_SUCCESS event - framework derives navigation from composition config.
     * This follows the CountersMainComponent pattern:
     * <ul>
     *   <li>Contract emits INTENT (action type only, no routes)</li>
     *   <li>Framework derives NAVIGATION from composition/router configuration</li>
     * </ul>
     */
    protected void onSaveSuccess() {
        // Emit generic success event - framework derives navigation from composition
        lookup.publish(EventKeys.ACTION_SUCCESS,
            new EventKeys.ActionResult(getClass()));
    }

    /**
     * Called when save fails.
     * <p>
     * Default: Does nothing (stays on page). Override to show error notification.
     */
    protected void onSaveFailure() {
        // Default: stay on page
    }

    @Override
    public List<AgentAction> agentActions() {
        String fieldNames = schema().fields().stream()
            .map(f -> f.name() + ":" + f.fieldType())
            .collect(Collectors.joining(", "));
        return List.of(
            new AgentAction("save", FORM_SUBMITTED,
                "Submit form data",
                "Map<String, Object>: {" + fieldNames + "}",
                PayloadParsers.toMapOfStringObject()),
            new AgentAction("cancel", CANCEL_REQUESTED,
                "Cancel and go back", null)
        );
    }

    @Override
    public ContractMetadata contractMetadata() {
        return new ContractMetadata(title(), "Form for creating a new entity", schema(), Map.of());
    }
}
