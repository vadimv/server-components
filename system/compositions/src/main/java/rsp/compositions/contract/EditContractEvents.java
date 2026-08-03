package rsp.compositions.contract;

import rsp.component.EventKey;

/** Events understood by edit contract components. */
public final class EditContractEvents {
    private EditContractEvents() {
    }

    public static final EventKey.VoidKey DELETE_REQUESTED = new EventKey.VoidKey("delete.requested");
}
