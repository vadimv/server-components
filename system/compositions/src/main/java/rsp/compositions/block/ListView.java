package rsp.compositions.block;

import rsp.compositions.schema.DataSchema;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** State and intents shared by list block components and list views. */
public final class ListView {
    private static final String LEGACY_SORT_FIELD = "__default__";

    private ListView() {
    }

    public sealed interface ListIntent permits SelectionChanged, BulkDeleteConfirmed, DeleteConfirmed,
            PageRequested, PageSizeRequested, SortRequested, QueryRequested, CreateRequested,
            EditRequested, DismissMessage {
    }

    public record SelectionChanged(Set<String> selectedIds) implements ListIntent {
        public SelectionChanged {
            selectedIds = selectedIds == null ? Set.of() : Set.copyOf(selectedIds);
        }
    }

    public record BulkDeleteConfirmed(Set<String> selectedIds) implements ListIntent {
        public BulkDeleteConfirmed {
            selectedIds = selectedIds == null ? Set.of() : Set.copyOf(selectedIds);
        }
    }

    public record DeleteConfirmed(String rowId) implements ListIntent {
        public DeleteConfirmed {
            if (rowId == null || rowId.isBlank()) {
                throw new IllegalArgumentException("rowId is required");
            }
        }
    }

    public record PageRequested(int page) implements ListIntent {
    }

    public record PageSizeRequested(int pageSize) implements ListIntent {
    }

    public record SortRequested(SortSpec sort) implements ListIntent {
        public SortRequested {
            Objects.requireNonNull(sort, "sort");
        }

        /** Compatibility constructor for the former direction-only intent. */
        public SortRequested(String direction) {
            this(new SortSpec(LEGACY_SORT_FIELD,
                    SortDirection.parse(direction, SortDirection.ASC)));
        }
    }

    public record QueryRequested(String search, Map<String, String> filters) implements ListIntent {
        public QueryRequested {
            search = search == null ? "" : search;
            filters = filters == null ? Map.of() : Map.copyOf(filters);
        }
    }

    public enum CreateRequested implements ListIntent {
        INSTANCE
    }

    public record EditRequested(String rowId) implements ListIntent {
    }

    public enum DismissMessage implements ListIntent {
        INSTANCE
    }

    public record ListViewState(List<Map<String, Object>> rows,
                                DataSchema schema,
                                ListQuery query,
                                long totalItems,
                                String modulePath,
                                Set<String> selectedIds,
                                String title,
                                EditTarget editTarget,
                                ListCapabilities capabilities,
                                String message,
                                boolean error,
                                ListStatus status) {
        public ListViewState {
            rows = rows == null ? List.of() : List.copyOf(rows);
            schema = schema == null ? new DataSchema(List.of()) : schema;
            query = query == null ? legacyQuery(schema, 1, "asc") : query;
            if (totalItems < 0) {
                throw new IllegalArgumentException("totalItems cannot be negative");
            }
            if (rows.size() > totalItems) {
                throw new IllegalArgumentException("rows cannot exceed totalItems");
            }
            modulePath = modulePath == null ? "/" : modulePath;
            selectedIds = selectedIds == null ? Set.of() : Set.copyOf(selectedIds);
            title = title == null ? "Items" : title;
            editTarget = editTarget == null ? EditTarget.overlay() : editTarget;
            capabilities = capabilities == null ? ListCapabilities.crud() : capabilities;
            message = message == null ? "" : message;
            status = status == null ? ListStatus.READY : status;
        }

        /** Compatibility constructor retained for state producers written before operation status was exposed. */
        public ListViewState(List<Map<String, Object>> rows,
                             DataSchema schema,
                             ListQuery query,
                             long totalItems,
                             String modulePath,
                             Set<String> selectedIds,
                             String title,
                             EditTarget editTarget,
                             ListCapabilities capabilities,
                             String message,
                             boolean error) {
            this(rows, schema, query, totalItems, modulePath, selectedIds, title, editTarget,
                    capabilities, message, error, ListStatus.READY);
        }

        /** Compatibility constructor retained for existing custom list views. */
        public ListViewState(List<Map<String, Object>> rows, DataSchema schema, int page, String sort,
                             String modulePath, Set<String> selectedIds, String title, EditTarget editTarget) {
            this(rows, schema, legacyQuery(schema, page, sort), rows == null ? 0 : rows.size(), modulePath,
                    selectedIds, title, editTarget, ListCapabilities.crud(), "", false);
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

        public int page() {
            return query.page();
        }

        public int pageSize() {
            return query.pageSize();
        }

        /** Compatibility accessor returning the current direction. */
        public String sort() {
            return query.sort() == null ? "asc" : query.sort().direction().queryValue();
        }

        public int totalPages() {
            if (totalItems == 0) return 0;
            long pages = 1 + (totalItems - 1) / query.pageSize();
            return (int) Math.min(pages, Integer.MAX_VALUE);
        }

        public boolean hasPrevious() {
            return query.page() > 1;
        }

        public boolean isBusy() {
            return status.isBusy();
        }

        public boolean hasNext() {
            return query.page() < totalPages();
        }

        public long firstVisibleItem() {
            return rows.isEmpty() ? 0 : (long) (query.page() - 1) * query.pageSize() + 1;
        }

        public long lastVisibleItem() {
            return rows.isEmpty() ? 0 : firstVisibleItem() + rows.size() - 1;
        }

        public ListViewState toggleSelection(String rowId) {
            if (rowId == null || rowId.isBlank()) {
                return this;
            }
            Set<String> updated = new HashSet<>(selectedIds);
            if (!updated.add(rowId)) {
                updated.remove(rowId);
            }
            return withSelection(updated);
        }

        public ListViewState selectAll() {
            Set<String> updated = new HashSet<>(selectedIds);
            rowIds().forEach(updated::add);
            return withSelection(updated);
        }

        public ListViewState clearSelection() {
            return withSelection(Set.of());
        }

        public boolean isSelected(String rowId) {
            return selectedIds.contains(rowId);
        }

        public boolean isAllSelected() {
            List<String> ids = rowIds();
            return !ids.isEmpty() && ids.size() == rows.size() && ids.stream().allMatch(selectedIds::contains);
        }

        public boolean isPartiallySelected() {
            List<String> ids = rowIds();
            long selectedOnPage = ids.stream().filter(selectedIds::contains).count();
            return selectedOnPage > 0 && selectedOnPage < ids.size();
        }

        public ListViewState withMessage(String value, boolean isError) {
            return new ListViewState(rows, schema, query, totalItems, modulePath, selectedIds, title,
                    editTarget, capabilities, value, isError, status);
        }

        public ListViewState withStatus(ListStatus value, String statusMessage) {
            return new ListViewState(rows, schema, query, totalItems, modulePath, selectedIds, title,
                    editTarget, capabilities, statusMessage, false, value);
        }

        public ListViewState withStatus(ListStatus value) {
            return new ListViewState(rows, schema, query, totalItems, modulePath, selectedIds, title,
                    editTarget, capabilities, message, error, value);
        }

        private ListViewState withSelection(Set<String> value) {
            return new ListViewState(rows, schema, query, totalItems, modulePath, value, title,
                    editTarget, capabilities, message, error, status);
        }

        private List<String> rowIds() {
            String rowKey = capabilities.rowKey();
            return rows.stream()
                    .map(row -> row.get(rowKey))
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .filter(id -> !id.isBlank())
                    .distinct()
                    .toList();
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

    static boolean isLegacySortField(String field) {
        return LEGACY_SORT_FIELD.equals(field);
    }

    private static ListQuery legacyQuery(DataSchema schema, int page, String direction) {
        String field = schema.columns().isEmpty() ? "id" : schema.columns().getFirst().name();
        return new ListQuery(Math.max(1, page), 10,
                new SortSpec(field, SortDirection.parse(direction, SortDirection.ASC)), "", Map.of());
    }
}
