package rsp.compositions.block;

import rsp.component.EventKey;

/** Events understood by edit block components. */
public final class EditBlockEvents {
    private EditBlockEvents() {
    }

    public static final EventKey.VoidKey DELETE_REQUESTED = new EventKey.VoidKey("delete.requested");
}
