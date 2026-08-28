package rsp.compositions.block;

import rsp.component.EventKey;

import java.util.Map;

/** Events understood by form block components and their agent actions. */
public final class FormBlockEvents {
    private FormBlockEvents() {
    }

    public static final EventKey.VoidKey CANCEL_REQUESTED = new EventKey.VoidKey("cancel.requested");
    @SuppressWarnings("unchecked")
    public static final EventKey.SimpleKey<Map<String, Object>> FORM_SUBMITTED =
            new EventKey.SimpleKey<>("form.submitted", (Class<Map<String, Object>>) (Class<?>) Map.class);
    @SuppressWarnings("unchecked")
    public static final EventKey.SimpleKey<Map<String, Object>> FORM_FIELD_SET =
            new EventKey.SimpleKey<>("form.field.set", (Class<Map<String, Object>>) (Class<?>) Map.class);
}
