package rsp.compositions;

import rsp.component.EventKey;

import java.util.Map;

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
     * State updated for query parameter.
     * Dynamic key: "stateUpdated.url.query.*"
     * Emitted by: ContextStateComponent
     * Handled by: AutoAddressBarSyncComponent
     */
    public static final EventKey.DynamicKey<String> STATE_UPDATED_QUERY =
            new EventKey.DynamicKey<>("stateUpdated.url.query", String.class);

    /**
     * State updated for path parameter.
     * Dynamic key: "stateUpdated.url.path.*"
     * Emitted by: ContextStateComponent
     * Handled by: AutoAddressBarSyncComponent
     */
    public static final EventKey.DynamicKey<String> STATE_UPDATED_PATH =
            new EventKey.DynamicKey<>("stateUpdated.url.path", String.class);
}
