package rsp.compositions.block;

/** Render-ready lifecycle state for a create or edit form. */
public enum FormStatus {
    READY,
    SUBMITTING,
    DELETING,
    NOT_FOUND,
    CONFLICT,
    LOAD_FAILED,
    FAILED;

    public boolean isBusy() {
        return this == SUBMITTING || this == DELETING;
    }

    public boolean canRenderFields() {
        return this != NOT_FOUND && this != LOAD_FAILED;
    }
}
