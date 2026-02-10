package rsp.compositions.composition;

/**
 * Slot - Defines where and how a view contract is rendered.
 * <p>
 * The Slot determines both layout position AND behavior:
 * <ul>
 *   <li>{@link #PRIMARY} - Full page content, navigated via Router URLs</li>
 *   <li>{@link #OVERLAY} - Popup/modal overlay, component state only (NO URL change)</li>
 *   <li>{@link #SECONDARY} - Split view panel (reserved for future use)</li>
 * </ul>
 * <p>
 * Slot.OVERLAY explicitly means popup/modal behavior - contracts with this slot
 * are pre-instantiated when their module's PRIMARY contract is shown, and are
 * triggered via component events (not URL navigation).
 */
public enum Slot {
    /**
     * Full page content area.
     * Contracts with this slot are navigated to via Router URLs.
     */
    PRIMARY,

    /**
     * Left sidebar panel.
     * Contracts with this slot are always visible alongside PRIMARY content.
     * Instantiated together with PRIMARY (not on-demand like OVERLAY).
     */
    LEFT_SIDEBAR,

    /**
     * Split view panel (reserved for future use).
     */
    SECONDARY,

    /**
     * Right sidebar panel.
     * Contracts with this slot are always visible alongside PRIMARY content.
     * Instantiated together with PRIMARY (not on-demand like OVERLAY).
     */
    RIGHT_SIDEBAR,

    /**
     * Popup/modal overlay.
     * Contracts with this slot are shown as modals/dialogs over the PRIMARY content.
     * They are triggered by component state changes, NOT by URL navigation.
     * No URL change occurs when an OVERLAY is opened or closed.
     */
    OVERLAY
}
