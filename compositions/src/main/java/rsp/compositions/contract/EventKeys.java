package rsp.compositions.contract;

import rsp.component.EventKey;
import rsp.component.definitions.ContextStateComponent;

import java.util.Map;
import java.util.Set;

import static rsp.compositions.contract.ActionBindings.*;

public final class EventKeys {
    private EventKeys() {}

    // ===== SHOW/HIDE EVENTS (Scene-level contract lifecycle) =====

    /**
     * Show a contract (on-demand instantiation).
     * Emitted by: Contracts (via ACTION binding translation)
     * Handled by: SceneComponent (instantiates contract, adds to scene)
     * Payload: ShowPayload with contract class and data
     * <p>
     * Data flow:
     * <ol>
     *   <li>View emits ACTION("edit", {id: "123"})</li>
     *   <li>Contract translates via actionBindings() to SHOW(EditContract.class, {id: "123"})</li>
     *   <li>SceneComponent receives SHOW, instantiates contract on-demand</li>
     * </ol>
     */
    public static final EventKey.SimpleKey<ShowPayload> SHOW =
            new EventKey.SimpleKey<>("show", ShowPayload.class);

    /**
     * Hide a contract (destroy instance).
     * Emitted by: Views (close button), Contracts (after save/delete)
     * Handled by: SceneComponent (calls onDestroy, removes from scene)
     * Payload: Contract class to hide (always explicit about what to close)
     * <p>
     * Unlike CLOSE_OVERLAY which is generic, HIDE always specifies which
     * contract to close. This supports multiple overlays being shown.
     */
    @SuppressWarnings("unchecked")
    public static final EventKey.SimpleKey<Class<? extends ViewContract>> HIDE =
            new EventKey.SimpleKey<>("hide",
                    (Class<Class<? extends ViewContract>>) (Class<?>) Class.class);

    /**
     * Abstract action event.
     * Emitted by: Views (DefaultListView buttons)
     * Handled by: Contracts (translated to SHOW via actionBindings())
     * Payload: ActionPayload with action name and data
     * <p>
     * Views emit abstract actions (e.g., "edit", "create") without
     * knowing about concrete contract classes. Contracts declare
     * bindings that translate these to SHOW events.
     * <p>
     * Example: ACTION("edit", {id: "123"}) → SHOW(PostEditContract.class, {id: "123"})
     */
    public static final EventKey.SimpleKey<ActionPayload> ACTION =
            new EventKey.SimpleKey<>("action", ActionPayload.class);

    // ===== FORM EVENTS =====

    /**
     * Form submitted with field values.
     * Emitted by: DefaultEditView
     * Handled by: EditViewContract.registerHandlers()
     */
    @SuppressWarnings("unchecked")
    public static final EventKey.SimpleKey<Map<String, Object>> FORM_SUBMITTED =
            new EventKey.SimpleKey<>("form.submitted",
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

    /**
     * Delete action requested (after confirmation).
     * Emitted by: DefaultEditView
     * Handled by: EditViewContract.registerHandlers()
     */
    public static final EventKey.VoidKey DELETE_REQUESTED =
            new EventKey.VoidKey("delete.requested");

    /**
     * Bulk delete action requested for selected rows.
     * Emitted by: DefaultListView (Delete Selected button)
     * Handled by: ListViewContract.registerHandlers()
     * Payload: Set of row IDs to delete
     */
    @SuppressWarnings("unchecked")
    public static final EventKey.SimpleKey<Set<String>> BULK_DELETE_REQUESTED =
            new EventKey.SimpleKey<>("bulk.delete.requested",
                    (Class<Set<String>>) (Class<?>) Set.class);

    // ===== MODAL EVENTS =====

    /**
     * Open create modal (MODAL edit mode).
     * Emitted by: DefaultListView
     * Handled by: LayoutComponent
     */
    public static final EventKey.VoidKey OPEN_CREATE_MODAL =
            new EventKey.VoidKey("openCreateModal");

    /**
     * Open edit modal with entity ID.
     * Emitted by: DefaultListView (Edit button)
     * Handled by: LayoutComponent, EditViewContract
     * Payload: Entity ID to edit
     */
    public static final EventKey.SimpleKey<String> OPEN_EDIT_MODAL =
            new EventKey.SimpleKey<>("openEditModal", String.class);

    /**
     * Close overlay (any mode).
     * Emitted by: LayoutComponent backdrop click
     * Handled by: LayoutComponent
     */
    public static final EventKey.VoidKey CLOSE_OVERLAY =
            new EventKey.VoidKey("closeOverlay");

    /**
     * Overlay close requested (for QUERY_PARAM mode URL update).
     * Emitted by: LayoutComponent when overlay closes
     * Handled by: Parent component for URL navigation
     */
    public static final EventKey.VoidKey OVERLAY_CLOSE_REQUESTED =
            new EventKey.VoidKey("overlayCloseRequested");

    /**
     * Modal save succeeded (close modal + refresh list).
     * Emitted by: EditViewContract.onSaveSuccess()
     * Handled by: LayoutComponent
     */
    public static final EventKey.VoidKey MODAL_SAVE_SUCCESS =
            new EventKey.VoidKey("modalSaveSuccess");

    /**
     * Modal delete succeeded (close modal + refresh list).
     * Emitted by: EditViewContract.onDeleteSuccess()
     * Handled by: LayoutComponent
     */
    public static final EventKey.VoidKey MODAL_DELETE_SUCCESS =
            new EventKey.VoidKey("modalDeleteSuccess");

    /**
     * Refresh list view (signal to reload data).
     * Emitted by: SceneComponent after ACTION_SUCCESS (OVERLAY mode)
     * Handled by: List view components (optional - for explicit refresh handling)
     * <p>
     * Note: PRIMARY mode same-route refresh uses direct state update instead
     * (stateUpdate.setState) which is the generic re-render mechanism.
     */
    public static final EventKey.VoidKey REFRESH_LIST =
            new EventKey.VoidKey("refreshList");

    // ===== STATE UPDATE EVENTS (Dynamic) =====

    /**
     * State updated event for any context parameter.
     * Dynamic key: "stateUpdated.*" for "stateUpdated.p", "stateUpdated.sort", etc.
     * Emitted by: DefaultListView (pagination, sorting)
     * Handled by: AddressBarSyncComponent, AutoAddressBarSyncComponent
     * Payload: ContextStateComponent.ContextValue.StringValue
     */
    public static final EventKey.DynamicKey<ContextStateComponent.ContextValue> STATE_UPDATED =
            new EventKey.DynamicKey<>("stateUpdated", ContextStateComponent.ContextValue.class);

    // ===== NAVIGATION EVENTS =====

    /**
     * Navigate to a URL path (SPA-style navigation).
     * Emitted by: Framework (SceneComponent) after ACTION_SUCCESS
     * Handled by: AutoAddressBarSyncComponent (uses PushHistory for internal paths)
     * Payload: The URL path to navigate to
     */
    public static final EventKey.SimpleKey<String> NAVIGATE =
            new EventKey.SimpleKey<>("navigate", String.class);

    /**
     * Reload page (full page reload).
     * Emitted by: Application code when full reload is needed
     * Handled by: AutoAddressBarSyncComponent (uses SetHref for full reload)
     * Payload: The URL path to reload
     */
    public static final EventKey.SimpleKey<String> RELOAD =
            new EventKey.SimpleKey<>("reload", String.class);

    // ===== ACTION RESULT EVENTS (Framework-Driven Navigation) =====

    /**
     * Action succeeded (generic success event).
     * Emitted by: Contracts (EditViewContract, FormViewContract) after successful operations
     * Handled by: Framework (SceneComponent) which decides navigation based on placement
     * Payload: ActionResult containing operation type and target route
     * <p>
     * This enables complete separation of concerns:
     * - Contracts emit generic success events (no placement knowledge)
     * - Framework decides what to do (navigate vs close overlay) based on slot
     * <p>
     * Example flow:
     * <ol>
     *   <li>Contract saves entity, emits ACTION_SUCCESS(SAVE, "/posts")</li>
     *   <li>SceneComponent receives event, checks contract placement</li>
     *   <li>If OVERLAY: emit HIDE + REFRESH_LIST</li>
     *   <li>If PRIMARY: emit NAVIGATE to target route</li>
     * </ol>
     */
    public static final EventKey.SimpleKey<ActionResult> ACTION_SUCCESS =
            new EventKey.SimpleKey<>("action.success", ActionResult.class);

    /**
     * Action failed (generic failure event).
     * Emitted by: Contracts when operations fail
     * Handled by: Framework (SceneComponent) or UI layer for error display
     * Payload: ActionResult containing operation type and error info
     */
    public static final EventKey.SimpleKey<ActionResult> ACTION_FAILURE =
            new EventKey.SimpleKey<>("action.failure", ActionResult.class);

    /**
     * Action result containing operation type and navigation target.
     * Framework uses this to decide navigation behavior based on contract placement.
     *
     * @param contractClass The class of the contract that performed the action
     * @param type The type of action that was performed (SAVE, DELETE, CANCEL)
     * @param targetRoute Where to navigate (for PRIMARY placement), or null if staying on page
     */
    public record ActionResult(
        Class<? extends ViewContract> contractClass,
        ActionType type,
        String targetRoute
    ) {}

    /**
     * Type of action performed by a contract.
     * Used by framework to determine appropriate UI response (e.g., which legacy event to emit).
     */
    public enum ActionType {
        /** Entity save operation */
        SAVE,
        /** Entity delete operation */
        DELETE,
        /** User cancelled operation */
        CANCEL
    }
}
