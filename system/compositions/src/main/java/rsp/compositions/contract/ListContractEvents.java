package rsp.compositions.contract;

import rsp.component.EventKey;

import java.util.Set;

/** Events understood by list contract components and their agent actions. */
public final class ListContractEvents {
    private ListContractEvents() {
    }

    public static final EventKey.VoidKey CREATE_ELEMENT_REQUESTED =
            new EventKey.VoidKey("list.create.element.requested");
    public static final EventKey.SimpleKey<String> EDIT_ELEMENT_REQUESTED =
            new EventKey.SimpleKey<>("list.edit.element", String.class);
    @SuppressWarnings("unchecked")
    public static final EventKey.SimpleKey<Set<String>> BULK_DELETE_REQUESTED =
            new EventKey.SimpleKey<>("bulk.delete.requested", (Class<Set<String>>) (Class<?>) Set.class);
    public static final EventKey.SimpleKey<Integer> PAGE_CHANGE_REQUESTED =
            new EventKey.SimpleKey<>("change.requested", Integer.class);
    public static final EventKey.VoidKey SELECT_ALL_REQUESTED =
            new EventKey.VoidKey("list.select.all.requested");
    public static final EventKey.VoidKey EDIT_SELECTED_REQUESTED =
            new EventKey.VoidKey("list.edit.selected.requested");
    public static final EventKey.VoidKey DELETE_SELECTED_REQUESTED =
            new EventKey.VoidKey("list.delete.selected.requested");
    public static final EventKey.SimpleKey<SelectedItems> SELECTION_CHANGED =
            new EventKey.SimpleKey<>("list.selection.changed", SelectedItems.class);

    public record SelectedItems(Set<String> ids) {
        public SelectedItems {
            ids = Set.copyOf(ids);
        }
    }
}
