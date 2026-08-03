package rsp.compositions.contract;

import rsp.compositions.schema.DataSchema;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** State and intents shared by list contract components and list views. */
public final class ListView {
    private ListView() {
    }

    public sealed interface ListIntent permits SelectionChanged, BulkDeleteConfirmed, PageRequested,
            SortRequested, CreateRequested, EditRequested {
    }

    public record SelectionChanged(Set<String> selectedIds) implements ListIntent {
        public SelectionChanged {
            selectedIds = Set.copyOf(selectedIds);
        }
    }

    public record BulkDeleteConfirmed(Set<String> selectedIds) implements ListIntent {
        public BulkDeleteConfirmed {
            selectedIds = Set.copyOf(selectedIds);
        }
    }

    public record PageRequested(int page) implements ListIntent {
    }

    public record SortRequested(String sort) implements ListIntent {
    }

    public enum CreateRequested implements ListIntent {
        INSTANCE
    }

    public record EditRequested(String rowId) implements ListIntent {
    }

    public record ListViewState(List<Map<String, Object>> rows,
                                DataSchema schema,
                                int page,
                                String sort,
                                String modulePath,
                                Set<String> selectedIds,
                                String title,
                                EditTarget editTarget) {
        public ListViewState {
            rows = rows == null ? List.of() : List.copyOf(rows);
            schema = schema == null ? new DataSchema(List.of()) : schema;
            sort = sort == null ? "asc" : sort;
            modulePath = modulePath == null ? "/" : modulePath;
            selectedIds = selectedIds == null ? Set.of() : Set.copyOf(selectedIds);
            title = title == null ? "Items" : title;
            editTarget = editTarget == null ? EditTarget.overlay() : editTarget;
        }

        public ListViewState(List<Map<String, Object>> rows, DataSchema schema, int page, String sort,
                             String modulePath, Set<String> selectedIds, String title) {
            this(rows, schema, page, sort, modulePath, selectedIds, title, EditTarget.overlay());
        }

        public ListViewState(List<Map<String, Object>> rows, DataSchema schema, int page, String sort,
                             String modulePath, Set<String> selectedIds) {
            this(rows, schema, page, sort, modulePath, selectedIds, "Items");
        }

        public ListViewState(List<Map<String, Object>> rows, DataSchema schema, int page, String sort,
                             String modulePath) {
            this(rows, schema, page, sort, modulePath, Set.of(), "Items");
        }

        public ListViewState toggleSelection(String rowId) {
            Set<String> updated = new HashSet<>(selectedIds);
            if (!updated.add(rowId)) {
                updated.remove(rowId);
            }
            return new ListViewState(rows, schema, page, sort, modulePath, updated, title, editTarget);
        }

        public ListViewState selectAll() {
            Set<String> updated = new HashSet<>(selectedIds);
            for (Map<String, Object> row : rows) {
                Object id = row.get("id");
                if (id != null) {
                    updated.add(String.valueOf(id));
                }
            }
            return new ListViewState(rows, schema, page, sort, modulePath, updated, title, editTarget);
        }

        public ListViewState clearSelection() {
            return new ListViewState(rows, schema, page, sort, modulePath, Set.of(), title, editTarget);
        }

        public boolean isSelected(String rowId) {
            return selectedIds.contains(rowId);
        }

        public boolean isAllSelected() {
            return !rows.isEmpty() && rows.stream()
                    .map(row -> row.get("id"))
                    .filter(java.util.Objects::nonNull)
                    .map(String::valueOf)
                    .allMatch(selectedIds::contains);
        }
    }

    public record EditTarget(boolean hasRoute, boolean opensAsOverlay, String routePattern) {
        public EditTarget {
            routePattern = routePattern == null ? "" : routePattern;
        }

        public static EditTarget overlay() {
            return new EditTarget(false, true, "");
        }
    }
}
