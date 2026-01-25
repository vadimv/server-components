package rsp.compositions;

import rsp.component.Lookup;
import rsp.compositions.schema.ValidationResult;

import java.util.Map;

import static rsp.compositions.EventKeys.FORM_SUBMITTED;

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
 *
 * @param <T> The type of entity being created or edited
 */
public abstract class FormViewContract<T> extends ViewContract {

    /**
     * Whether this contract is currently the active overlay.
     * Used to prevent handling events when another overlay is active.
     * Only relevant for overlay contracts (IS_OVERLAY_MODE = true).
     */
    private boolean isActiveOverlay = false;

    /**
     * Whether this contract is running in modal/overlay mode.
     */
    protected final boolean isModalMode;

    protected FormViewContract(final Lookup lookup) {
        super(lookup);

        // Check if running as an overlay (modal/popup) via Slot.OVERLAY
        final Boolean overlayMode = lookup.get(ContextKeys.IS_OVERLAY_MODE);
        this.isModalMode = overlayMode != null && overlayMode;

        // Handle form submission - only if this is the active overlay (or not in overlay mode)
        lookup.subscribe(FORM_SUBMITTED, (eventName, fieldValues) -> {
            if (shouldHandleEvent()) {
                handleFormSubmitted(fieldValues, isModalMode);
            }
        });

        // Track overlay deactivation
        if (isModalMode) {
            lookup.subscribe(EventKeys.CLOSE_OVERLAY, () -> {
                isActiveOverlay = false;
            });

            lookup.subscribe(EventKeys.MODAL_SAVE_SUCCESS, () -> {
                isActiveOverlay = false;
            });

            lookup.subscribe(EventKeys.MODAL_DELETE_SUCCESS, () -> {
                isActiveOverlay = false;
            });
        } else {
            // Non-overlay (PRIMARY) mode - always active
            isActiveOverlay = true;
        }
    }

    /**
     * Set this overlay as active. Called by subclasses when the overlay is opened.
     */
    protected void setActive() {
        this.isActiveOverlay = true;
    }

    /**
     * Check if this contract should handle events.
     * Returns true if:
     * - Not in overlay mode (PRIMARY slot), or
     * - In overlay mode AND this is the currently active overlay
     */
    protected boolean shouldHandleEvent() {
        return !isModalMode || isActiveOverlay;
    }

    /**
     * Check if the contract is in create mode.
     * <p>
     * Used by views to adapt rendering (e.g., hide delete button in create mode).
     *
     * @return true if creating a new entity, false if editing existing
     */
    public abstract boolean isCreateMode();

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
     * Default implementation uses convention based on the route pattern:
     * Removes the last segment if it's a parameter (starts with :).
     * <p>
     * Also restores query parameters that were preserved from the list view
     * (e.g., fromP → p, fromSort → sort).
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

        // Convention: remove last segment if it's a parameter or token
        String basePath;
        int lastSlash = routePattern.lastIndexOf('/');
        if (lastSlash > 0) {
            String lastSegment = routePattern.substring(lastSlash + 1);

            // Strip last segment if it's a parameter (starts with :) or a token
            if (lastSegment.startsWith(":") || isPathToken(lastSegment)) {
                basePath = routePattern.substring(0, lastSlash);
            } else {
                basePath = routePattern;
            }
        } else {
            basePath = routePattern;
        }

        // Restore query parameters from url.query.from* attributes
        String queryString = buildRestoredQueryString();
        if (queryString.isEmpty()) {
            return basePath;
        }

        return basePath + "?" + queryString;
    }

    /**
     * Check if a path segment is a recognized token (e.g., "new", "create").
     * <p>
     * Override to recognize custom tokens.
     *
     * @param segment The path segment to check
     * @return true if it's a token that should be stripped for list route
     */
    protected boolean isPathToken(String segment) {
        return "new".equals(segment) || "create".equals(segment);
    }

    /**
     * Build query string by restoring from* parameters to original names.
     *
     * @return Query string (e.g., "p=3&sort=desc"), or empty string
     */
    private String buildRestoredQueryString() {
        java.util.List<String> params = new java.util.ArrayList<>();

        // Restore page parameter (fromP → p)
        String fromP = lookup.get(ContextKeys.URL_QUERY.with("fromP"));
        if (fromP != null && !fromP.isEmpty()) {
            params.add("p=" + fromP);
        }

        // Restore sort parameter (fromSort → sort)
        String fromSort = lookup.get(ContextKeys.URL_QUERY.with("fromSort"));
        if (fromSort != null && !fromSort.isEmpty()) {
            params.add("sort=" + fromSort);
        }

        return params.isEmpty() ? "" : String.join("&", params);
    }

    // ========================================================================
    // Event Handling
    // ========================================================================

    /**
     * Handle form submission event.
     * <p>
     * Validates field values, then calls {@link #save(Map)} on success.
     *
     * @param fieldValues The submitted field values
     * @param isModalMode Whether in modal mode
     */
    protected void handleFormSubmitted(Map<String, Object> fieldValues,
                                       boolean isModalMode) {
        // Validate before saving
        ValidationResult result = schema().validate(fieldValues);
        if (!result.isValid()) {
            onValidationFailed(result);
            return;
        }

        boolean success = save(fieldValues);
        if (success) {
            onSaveSuccess(isModalMode);
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
     * Default: In modal mode, publishes "modalSaveSuccess". Otherwise, navigates to list.
     *
     * @param isModalMode Whether in modal mode
     */
    protected void onSaveSuccess(boolean isModalMode) {
        if (isModalMode) {
            lookup.publish(EventKeys.MODAL_SAVE_SUCCESS);
        } else {
            lookup.publish(EventKeys.NAVIGATE, listRoute());
        }
    }

    /**
     * Called when save fails.
     * <p>
     * Default: Does nothing (stays on page). Override to show error notification.
     */
    protected void onSaveFailure() {
        // Default: stay on page
    }
}
