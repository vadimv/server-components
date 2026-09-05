package rsp.compositions.block;

/** Lifecycle status for list operations that must not be submitted more than once. */
public enum ListStatus {
    READY,
    DELETING;

    public boolean isBusy() {
        return this != READY;
    }
}
