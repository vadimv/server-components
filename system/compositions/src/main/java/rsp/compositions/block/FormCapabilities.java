package rsp.compositions.block;

/**
 * Operations exposed by a form view and accepted by its owning block.
 *
 * @param canSave whether the current draft may be submitted
 * @param canDelete whether the current entity may be deleted
 * @param canCancel whether the form may be dismissed or returned from
 */
public record FormCapabilities(boolean canSave, boolean canDelete, boolean canCancel) {
    public static FormCapabilities create() {
        return new FormCapabilities(true, false, true);
    }

    public static FormCapabilities edit() {
        return new FormCapabilities(true, true, true);
    }
}
