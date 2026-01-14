package rsp.compositions;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentSegment;
import rsp.component.Subscriber;
import rsp.page.events.Command;
import rsp.page.events.ComponentEventNotification;
import rsp.page.events.GenericTaskEvent;
import rsp.page.events.RemoteCommand;

import java.util.Map;
import java.util.function.Consumer;

import static rsp.compositions.EventKeys.DELETE_REQUESTED;
import static rsp.compositions.EventKeys.FORM_SUBMITTED;

/**
 * EditViewContract - Base contract for edit/form views.
 * <p>
 * Provides the entity being edited and schema metadata for adaptive rendering.
 * Concrete implementations load entities by ID extracted from URL path parameters.
 * <p>
 * Supports create mode detection via {@link #isCreateMode()} which checks if the
 * resolved ID matches the configurable create token (default: "new").
 *
 * @param <T> The type of entity being edited
 */
public abstract class EditViewContract<T> extends ViewContract {

    protected EditViewContract(ComponentContext context) {
        super(context);

        final NavigationContext navigationContext = new NavigationContext(context);
        final boolean isModalMode = context.get(ContextKeys.MODAL_OVERLAY_VIEW_CONTRACT) != null;
        final CommandsEnqueue commandsEnqueue = context.get(CommandsEnqueue.class);
        final Subscriber subscriber = context.get(Subscriber.class);
        // Handle form submission
        subscriber.addEventHandler(FORM_SUBMITTED, (eventName, fieldValues) -> {
            handleFormSubmitted(fieldValues, commandsEnqueue, navigationContext, isModalMode);
        }, false);

        // Handle delete request
        subscriber.addEventHandler(DELETE_REQUESTED, () -> {
            handleDeleteRequested(commandsEnqueue, navigationContext, isModalMode);
        }, false);

    }

    /**
     * Get the token used to identify create mode in the URL path.
     * <p>
     * When the resolved ID equals this token, the contract is in create mode.
     * Override to customize (e.g., "_", "create", "0").
     *
     * @return The create token (default: "new")
     */
    protected String createToken() {
        return "new";
    }

    /**
     * Resolve the entity ID from the URL path.
     * <p>
     * Subclasses must implement this to extract the ID from path parameters.
     * Used by {@link #isCreateMode()} to detect create mode.
     *
     * @return The resolved ID, or null if not present
     */
    protected abstract String resolveId();

    /**
     * Check if the contract is in create mode.
     * <p>
     * Create mode is detected when:
     * <ul>
     *   <li>The resolved ID is null, or</li>
     *   <li>The resolved ID equals the create token</li>
     * </ul>
     *
     * @return true if in create mode, false if editing existing entity
     */
    public boolean isCreateMode() {
        String id = resolveId();
        return id == null || id.equals(createToken());
    }

    /**
     * Load the entity to be edited.
     * <p>
     * Typically reads an ID from path parameters and loads from a service.
     * Should return null when in create mode (empty form).
     *
     * @return The entity to edit, or null if not found or in create mode
     */
    public abstract T item();

    /**
     * Get the schema for the entity's fields.
     * <p>
     * Can be auto-derived from the entity if not explicitly provided.
     *
     * @return Schema metadata for rendering form fields
     */
    public abstract ListSchema schema();

    /**
     * Save the entity with the given field values.
     * <p>
     * Called when the user clicks the Save button in the edit form.
     *
     * @param fieldValues Map of field names to values from the form
     * @return true if save succeeded, false otherwise
     */
    public abstract boolean save(java.util.Map<String, Object> fieldValues);

    /**
     * Delete the current entity.
     * <p>
     * Called when the user clicks the Delete button in the edit form.
     * Only valid in edit mode (not create mode).
     * <p>
     * Default implementation throws {@link UnsupportedOperationException}.
     * Override to implement deletion logic.
     *
     * @return true if deletion succeeded, false otherwise
     * @throws IllegalStateException if called in create mode
     */
    public boolean delete() {
        if (isCreateMode()) {
            throw new IllegalStateException("Cannot delete in create mode");
        }
        throw new UnsupportedOperationException("Delete not implemented for " + getClass().getSimpleName());
    }

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
     *   <li>{@code "/posts/:id" with fromP=3 → "/posts?p=3"}</li>
     *   <li>{@code "/posts/:id" with fromP=3&fromSort=desc → "/posts?p=3&sort=desc"}</li>
     *   <li>{@code "/admin/users/:userId" → "/admin/users"}</li>
     *   <li>{@code "/posts/:postId/comments/:id" → "/posts/:postId/comments"}</li>
     * </ul>
     * <p>
     * Override this method if your navigation flow doesn't follow this convention
     * (e.g., nested parameterized routes, cross-module navigation, or custom query params).
     *
     * @return The list route path with restored query parameters
     */
    public String listRoute() {
        String routePattern = context.get(ContextKeys.ROUTE_PATTERN);
        if (routePattern == null) {
            throw new IllegalStateException("route.pattern not found in context");
        }

        // Convention: remove last segment if it's a parameter or the create token
        // "/posts/:id" → "/posts"
        // "/posts/new" → "/posts"
        String basePath;
        int lastSlash = routePattern.lastIndexOf('/');
        if (lastSlash > 0) {
            String lastSegment = routePattern.substring(lastSlash + 1);

            // Strip last segment if it's a parameter (starts with :) or the create token
            if (lastSegment.startsWith(":") || lastSegment.equals(createToken())) {
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
     * Build query string by restoring from* parameters to original names.
     * <p>
     * Reads url.query.fromP, url.query.fromSort, etc. from context
     * and converts them back to original names (fromP → p, fromSort → sort).
     *
     * @return Query string (e.g., "p=3&sort=desc"), or empty string
     */
    private String buildRestoredQueryString() {
        java.util.List<String> params = new java.util.ArrayList<>();

        // Restore page parameter (fromP → p)
        String fromP = context.get(ContextKeys.URL_QUERY.with("fromP"));
        if (fromP != null && !fromP.isEmpty()) {
            params.add("p=" + fromP);
        }

        // Restore sort parameter (fromSort → sort)
        String fromSort = context.get(ContextKeys.URL_QUERY.with("fromSort"));
        if (fromSort != null && !fromSort.isEmpty()) {
            params.add("sort=" + fromSort);
        }

        return params.isEmpty() ? "" : String.join("&", params);
    }

    // ========================================================================
    // Event Handling - Contract registers handlers for form events
    // ========================================================================

    /**
     * Register event handlers for this contract.
     * <p>
     * Called by the framework after the view component is created.
     * The contract handles events emitted by the view (form.submitted, delete.requested)
     * and decides what actions to take.
     * <p>
     * Override this method to customize event handling behavior.
     *
     * @param segment The component segment to register handlers on
     * @param commandsEnqueue Consumer for emitting commands/events
     * @param navigationContext Context for navigation operations
     * @param isModalMode Whether the contract is rendered in a modal overlay
     */
    public void registerHandlers(ComponentSegment<?> segment,
                                 CommandsEnqueue commandsEnqueue,
                                 NavigationContext navigationContext,
                                 boolean isModalMode) {
        // Handle form submission
        segment.addEventHandler(FORM_SUBMITTED, (eventName, fieldValues) -> {
            handleFormSubmitted(fieldValues, commandsEnqueue, navigationContext, isModalMode);
        }, false);

        // Handle delete request
        segment.addEventHandler(DELETE_REQUESTED, () -> {
            handleDeleteRequested(commandsEnqueue, navigationContext, isModalMode);
        }, false);
    }

    /**
     * Handle form submission event.
     * <p>
     * Default implementation calls {@link #save(Map)} and navigates on success.
     * Override to customize (e.g., add validation, custom success handling).
     *
     * @param fieldValues The submitted field values
     * @param commandsEnqueue Consumer for emitting commands
     * @param navigationContext Context for navigation
     * @param isModalMode Whether in modal mode
     */
    protected void handleFormSubmitted(Map<String, Object> fieldValues,
                                       CommandsEnqueue commandsEnqueue,
                                       NavigationContext navigationContext,
                                       boolean isModalMode) {
        boolean success = save(fieldValues);
        if (success) {
            onSaveSuccess(commandsEnqueue, navigationContext, isModalMode);
        } else {
            onSaveFailure(commandsEnqueue);
        }
    }

    /**
     * Handle delete request event.
     * <p>
     * Default implementation calls {@link #delete()} and navigates on success.
     * Override to customize delete handling.
     *
     * @param commandsEnqueue Consumer for emitting commands
     * @param navigationContext Context for navigation
     * @param isModalMode Whether in modal mode
     */
    protected void handleDeleteRequested(CommandsEnqueue commandsEnqueue,
                                         NavigationContext navigationContext,
                                         boolean isModalMode) {
        boolean success = delete();
        if (success) {
            onDeleteSuccess(commandsEnqueue, navigationContext, isModalMode);
        } else {
            onDeleteFailure(commandsEnqueue);
        }
    }

    /**
     * Called when save succeeds.
     * <p>
     * Default: In modal mode, emits "modalSaveSuccess". Otherwise, navigates to list.
     *
     * @param commandsEnqueue Consumer for emitting commands
     * @param navigationContext Context for navigation
     * @param isModalMode Whether in modal mode
     */
    protected void onSaveSuccess(CommandsEnqueue commandsEnqueue,
                                 NavigationContext navigationContext,
                                 boolean isModalMode) {
        if (isModalMode) {
            commandsEnqueue.offer(new ComponentEventNotification("modalSaveSuccess", Map.of()));
        } else {
            commandsEnqueue.offer(new RemoteCommand.SetHref(listRoute()));
        }
    }

    /**
     * Called when save fails.
     * <p>
     * Default: Does nothing (stays on page). Override to show error notification.
     *
     * @param commandsEnqueue Consumer for emitting commands
     */
    protected void onSaveFailure(CommandsEnqueue commandsEnqueue) {
        // Default: stay on page, could emit error notification
    }

    /**
     * Called when delete succeeds.
     * <p>
     * Default: In modal mode, emits "modalDeleteSuccess". Otherwise, navigates to list.
     *
     * @param commandsEnqueue Consumer for emitting commands
     * @param navigationContext Context for navigation
     * @param isModalMode Whether in modal mode
     */
    protected void onDeleteSuccess(CommandsEnqueue commandsEnqueue,
                                   NavigationContext navigationContext,
                                   boolean isModalMode) {
        if (isModalMode) {
            commandsEnqueue.offer(new ComponentEventNotification("modalDeleteSuccess", Map.of()));
        } else {
            commandsEnqueue.offer(new RemoteCommand.SetHref(listRoute()));
        }
    }

    /**
     * Called when delete fails.
     * <p>
     * Default: Does nothing (stays on page). Override to show error notification.
     *
     * @param commandsEnqueue Consumer for emitting commands
     */
    protected void onDeleteFailure(CommandsEnqueue commandsEnqueue) {
        // Default: stay on page, could emit error notification
    }
}
