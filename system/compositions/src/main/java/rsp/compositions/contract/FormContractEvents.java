package rsp.compositions.contract;

import rsp.component.EventKey;

import java.util.Map;

/** Events understood by form contract components and their agent actions. */
public final class FormContractEvents {
    private FormContractEvents() {
    }

    public static final EventKey.VoidKey CANCEL_REQUESTED = new EventKey.VoidKey("cancel.requested");
    @SuppressWarnings("unchecked")
    public static final EventKey.SimpleKey<Map<String, Object>> FORM_SUBMITTED =
            new EventKey.SimpleKey<>("form.submitted", (Class<Map<String, Object>>) (Class<?>) Map.class);
    @SuppressWarnings("unchecked")
    public static final EventKey.SimpleKey<Map<String, Object>> FORM_FIELD_SET =
            new EventKey.SimpleKey<>("form.field.set", (Class<Map<String, Object>>) (Class<?>) Map.class);
}
