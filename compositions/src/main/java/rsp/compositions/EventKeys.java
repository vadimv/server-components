package rsp.compositions;

import rsp.component.EventKey;
import rsp.component.definitions.ContextStateComponent;

import java.util.Map;
import java.util.Set;

public final class EventKeys {
    private EventKeys() {}

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
     * Refresh list view.
     * Emitted by: LayoutComponent after modal success
     * Handled by: ListView
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
     * Navigate to a URL path.
     * Emitted by: EditViewContract after save/delete success
     * Handled by: Framework (triggers browser navigation via RemoteCommand.SetHref)
     * Payload: The URL path to navigate to
     */
    public static final EventKey.SimpleKey<String> NAVIGATE =
            new EventKey.SimpleKey<>("navigate", String.class);
}
