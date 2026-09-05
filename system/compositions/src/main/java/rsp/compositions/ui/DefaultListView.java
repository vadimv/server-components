package rsp.compositions.ui;

import rsp.component.ComponentView;
import rsp.component.IntentDispatcher;
import rsp.compositions.block.ListQuery;
import rsp.compositions.block.ListView;
import rsp.compositions.block.SortDirection;
import rsp.compositions.block.SortSpec;
import rsp.compositions.schema.ColumnConfig;
import rsp.compositions.schema.FieldDef;
import rsp.compositions.schema.FieldType;
import rsp.compositions.schema.TextAlign;
import rsp.dsl.Definition;
import rsp.ref.ElementRef;
import rsp.util.json.JsonDataType;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static rsp.dsl.Html.*;

/** Default schema-driven data-grid view for {@link rsp.compositions.block.ListBlock}. */
public class DefaultListView implements ComponentView<ListView.ListViewState, ListView.ListIntent> {
    private static final List<Integer> STANDARD_PAGE_SIZES = List.of(10, 25, 50, 100);

    @Override
    public rsp.component.View<ListView.ListViewState> resolve(IntentDispatcher<ListView.ListIntent> intents) {
        return state -> {
            boolean selectable = state.schema().selectable();
            List<FieldDef> columns = state.schema().listColumns();
            boolean hasRowActions = state.capabilities().canEdit() || state.capabilities().canDelete();
            String currentQueryParams = buildReturnQuery(state.query());

            return div(
                    attr("class", "data-grid"),
                    state.isBusy() ? attr("aria-busy", "true") : of(),
                    h1(text(state.title())),
                    renderMessage(state, intents),
                    renderActions(state, intents),
                    renderQueryControls(state, intents),
                    renderPagination("top", state, intents),
                    div(
                            attr("class", "data-grid-table-wrap"),
                            table(
                                    attr("aria-label", state.title()),
                                    caption(attr("class", "sr-only"), text(state.title())),
                                    thead(
                                            tr(
                                                    selectable ? renderSelectAllHeader(state, intents) : of(),
                                                    of(columns.stream().map(field -> renderHeader(field, state, intents))),
                                                    hasRowActions
                                                            ? th(attr("scope", "col"), attr("class", "grid-actions-column"), text("Actions"))
                                                            : of()
                                            )
                                    ),
                                    tbody(
                                            state.rows().isEmpty()
                                                    ? renderEmptyRow(state, columns.size(), selectable, hasRowActions)
                                                    : of(state.rows().stream().map(row -> renderRow(
                                                            row, columns, selectable, hasRowActions,
                                                            currentQueryParams, state, intents)))
                                    )
                            )
                    ),
                    renderPagination("bottom", state, intents)
            );
        };
    }

    private Definition renderMessage(ListView.ListViewState state,
                                     IntentDispatcher<ListView.ListIntent> intents) {
        if (state.message().isBlank()) {
            return of();
        }
        return div(
                attr("class", state.error() ? "grid-message grid-message-error" : "grid-message grid-message-success"),
                attr("role", state.error() ? "alert" : "status"),
                span(text(state.message())),
                button(
                        attr("type", "button"),
                        attr("class", "grid-message-dismiss"),
                        attr("aria-label", "Dismiss message"),
                        state.isBusy() ? attr("disabled", "disabled") : of(),
                        text("×"),
                        state.isBusy() ? of() : on("click", _ -> intents.dispatch(ListView.DismissMessage.INSTANCE)))
        );
    }

    private Definition renderActions(ListView.ListViewState state,
                                     IntentDispatcher<ListView.ListIntent> intents) {
        boolean showBulkDelete = state.schema().selectable()
                && state.capabilities().canDelete()
                && !state.selectedIds().isEmpty();
        return div(
                attr("class", "list-actions"),
                state.capabilities().canCreate()
                        ? button(
                                attr("type", "button"),
                                attr("class", "create-button"),
                                state.isBusy() ? attr("disabled", "disabled") : of(),
                                text("Create New"),
                                state.isBusy() ? of()
                                        : on("click", _ -> intents.dispatch(ListView.CreateRequested.INSTANCE)))
                        : of(),
                showBulkDelete ? renderBulkDeleteButton(state, intents) : of()
        );
    }

    private Definition renderQueryControls(ListView.ListViewState state,
                                           IntentDispatcher<ListView.ListIntent> intents) {
        ElementRef searchRef = createElementRef();
        Map<String, ElementRef> filterRefs = new LinkedHashMap<>();
        List<FieldDef> filterable = state.schema().listColumns().stream()
                .filter(field -> state.schema().columnConfig(field.name()).filterable())
                .toList();
        filterable.forEach(field -> filterRefs.put(field.name(), createElementRef()));

        return form(
                attr("class", "grid-query"),
                div(
                        attr("class", "grid-query-field grid-search-field"),
                        label(attr("for", "grid-search"), text("Search")),
                        input(
                                ref(searchRef),
                                attr("id", "grid-search"),
                                attr("type", "search"),
                                attr("name", "q"),
                                attr("value", state.query().search()),
                                state.isBusy() ? attr("disabled", "disabled") : of(),
                                attr("placeholder", "Search all columns"))
                ),
                of(filterable.stream().map(field -> div(
                        attr("class", "grid-query-field"),
                        label(text(field.displayName()),
                                input(
                                        ref(filterRefs.get(field.name())),
                                        attr("type", "search"),
                                        attr("name", "filter." + field.name()),
                                        attr("value", state.query().filters().getOrDefault(field.name(), "")),
                                        state.isBusy() ? attr("disabled", "disabled") : of(),
                                        attr("placeholder", "Filter " + field.displayName())))
                ))),
                div(
                        attr("class", "grid-query-actions"),
                        button(attr("type", "submit"),
                                state.isBusy() ? attr("disabled", "disabled") : of(), text("Apply")),
                        button(
                                attr("type", "button"),
                                attr("class", "grid-clear-button"),
                                state.isBusy() ? attr("disabled", "disabled") : of(),
                                text("Clear"),
                                state.isBusy() ? of()
                                        : on("click", _ -> intents.dispatch(new ListView.QueryRequested("", Map.of()))))
                ),
                state.isBusy() ? of()
                        : on("submit", true, context -> dispatchQuery(context, searchRef, filterRefs, intents))
        );
    }

    private void dispatchQuery(rsp.page.EventContext context,
                               ElementRef searchRef,
                               Map<String, ElementRef> filterRefs,
                               IntentDispatcher<ListView.ListIntent> intents) {
        CompletableFuture<String> search = stringProperty(context, searchRef);
        Map<String, CompletableFuture<String>> filterValues = new LinkedHashMap<>();
        filterRefs.forEach((name, elementRef) -> filterValues.put(name, stringProperty(context, elementRef)));
        List<CompletableFuture<?>> futures = new ArrayList<>(filterValues.values());
        futures.add(search);
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenRun(() -> {
            Map<String, String> filters = new LinkedHashMap<>();
            filterValues.forEach((name, future) -> {
                String value = future.join();
                if (!value.isBlank()) filters.put(name, value);
            });
            intents.dispatch(new ListView.QueryRequested(search.join(), filters));
        });
    }

    private CompletableFuture<String> stringProperty(rsp.page.EventContext context, ElementRef ref) {
        return context.propertiesByRef(ref).get("value").thenApply(value ->
                value instanceof JsonDataType.String string ? string.value() : "");
    }

    private Definition renderSelectAllHeader(ListView.ListViewState state,
                                             IntentDispatcher<ListView.ListIntent> intents) {
        String selectionState = state.isAllSelected() ? "true" : state.isPartiallySelected() ? "mixed" : "false";
        return th(
                attr("scope", "col"),
                attr("class", "grid-select-column"),
                input(
                        attr("type", "checkbox"),
                        attr("aria-label", "Select all rows on this page"),
                        attr("aria-checked", selectionState),
                        attr("data-selection-state", selectionState),
                        state.isBusy() ? attr("disabled", "disabled") : of(),
                        state.isAllSelected() ? attr("checked", "checked") : of(),
                        state.isBusy() ? of() : on("click", _ -> {
                            ListView.ListViewState updated = state.isAllSelected()
                                    ? state.clearSelection()
                                    : state.selectAll();
                            intents.dispatch(new ListView.SelectionChanged(updated.selectedIds()));
                        }))
        );
    }

    private Definition renderHeader(FieldDef field,
                                    ListView.ListViewState state,
                                    IntentDispatcher<ListView.ListIntent> intents) {
        ColumnConfig config = state.schema().columnConfig(field.name());
        SortSpec active = state.query().sort();
        boolean activeColumn = active != null && active.field().equals(field.name());
        String ariaSort = !activeColumn ? "none"
                : active.direction() == SortDirection.ASC ? "ascending" : "descending";
        Definition content;
        if (config.sortable()) {
            SortDirection next = activeColumn ? active.direction().opposite() : SortDirection.ASC;
            content = button(
                    attr("type", "button"),
                    attr("class", "grid-sort-button"),
                    state.isBusy() ? attr("disabled", "disabled") : of(),
                    text(field.displayName()),
                    span(
                            attr("class", "grid-sort-indicator"),
                            attr("aria-hidden", "true"),
                            text(activeColumn ? active.direction() == SortDirection.ASC ? " ↑" : " ↓" : "")),
                    state.isBusy() ? of() : on("click", _ -> intents.dispatch(
                            new ListView.SortRequested(new SortSpec(field.name(), next)))));
        } else {
            content = text(field.displayName());
        }
        return th(
                attr("scope", "col"),
                activeColumn ? attr("aria-sort", ariaSort) : of(),
                attr("class", columnClass(config) + (config.sortable() ? " sortable" : "")),
                config.width() == null ? of() : attr("style", "width: " + config.width() + ";"),
                content
        );
    }

    private Definition renderRow(Map<String, Object> row,
                                 List<FieldDef> columns,
                                 boolean selectable,
                                 boolean hasRowActions,
                                 String currentQueryParams,
                                 ListView.ListViewState state,
                                 IntentDispatcher<ListView.ListIntent> intents) {
        String rowId = rowId(row, state.capabilities().rowKey());
        return tr(
                selectable
                        ? td(
                                attr("class", "grid-select-column"),
                                input(
                                        attr("type", "checkbox"),
                                        attr("aria-label", "Select row " + rowId),
                                        state.isBusy() ? attr("disabled", "disabled") : of(),
                                        state.isSelected(rowId) ? attr("checked", "checked") : of(),
                                        state.isBusy() ? of() : on("click", _ -> {
                                            ListView.ListViewState updated = state.toggleSelection(rowId);
                                            intents.dispatch(new ListView.SelectionChanged(updated.selectedIds()));
                                        })))
                        : of(),
                of(columns.stream().map(field -> {
                    ColumnConfig config = state.schema().columnConfig(field.name());
                    return td(
                            attr("class", columnClass(config)
                                    + (field.fieldType() == FieldType.TEXT ? " grid-long-text" : "")),
                            config.width() == null ? of() : attr("style", "width: " + config.width() + ";"),
                            renderValue(row.get(field.name()), field, config));
                })),
                hasRowActions ? td(
                        attr("class", "grid-row-actions"),
                        state.capabilities().canEdit()
                                ? renderEditButton(rowId, currentQueryParams, state.editTarget(), state.isBusy(), intents)
                                : of(),
                        state.capabilities().canDelete()
                                ? renderDeleteButton(state.modulePath(), rowId, state.isBusy(), intents)
                                : of()) : of()
        );
    }

    private Definition renderEmptyRow(ListView.ListViewState state,
                                      int dataColumnCount,
                                      boolean selectable,
                                      boolean hasRowActions) {
        int colspan = dataColumnCount + (selectable ? 1 : 0) + (hasRowActions ? 1 : 0);
        boolean constrained = !state.query().search().isBlank() || !state.query().filters().isEmpty();
        String message = state.error()
                ? "Items could not be loaded."
                : constrained ? "No items match the current search and filters." : "No items to display.";
        return tr(td(attr("colspan", String.valueOf(Math.max(1, colspan))),
                attr("class", "grid-empty"), text(message)));
    }

    private Definition renderBulkDeleteButton(ListView.ListViewState state,
                                              IntentDispatcher<ListView.ListIntent> intents) {
        int count = state.selectedIds().size();
        ElementRef dialogRef = createElementRef();
        Set<String> selectedIds = Set.copyOf(state.selectedIds());
        return of(
                button(
                        attr("type", "button"),
                        attr("class", "btn-delete btn-danger"),
                        state.isBusy() ? attr("disabled", "disabled") : of(),
                        text("Delete Selected (" + count + ")"),
                        state.isBusy() ? of() : on("click", context -> context.showModal(dialogRef))),
                ConfirmationDialog.render(
                        state.modulePath() + ":bulk-delete", dialogRef,
                        ConfirmationDialog.Spec.danger(
                                "Delete selected items?",
                                "This will permanently delete " + count + " selected "
                                        + (count == 1 ? "item." : "items."),
                                count == 1 ? "Delete item" : "Delete " + count + " items"),
                        intents, new ListView.BulkDeleteConfirmed(selectedIds)));
    }

    private Definition renderEditButton(String rowId,
                                        String queryParams,
                                        ListView.EditTarget editTarget,
                                        boolean disabled,
                                        IntentDispatcher<ListView.ListIntent> intents) {
        if (editTarget.hasRoute() && !editTarget.opensAsOverlay()) {
            String editUrl = editTarget.routePattern().replace(":id", rowId);
            if (!queryParams.isEmpty()) editUrl += "?" + queryParams;
            return a(disabled ? of() : attr("href", editUrl), attr("class", "edit-button edit-link"),
                    disabled ? attr("aria-disabled", "true") : of(),
                    disabled ? attr("tabindex", "-1") : of(),
                    attr("aria-label", "Edit row " + rowId), text("Edit"));
        }
        return button(
                attr("type", "button"),
                attr("class", "edit-button"),
                attr("aria-label", "Edit row " + rowId),
                disabled ? attr("disabled", "disabled") : of(),
                text("Edit"),
                disabled ? of() : on("click", _ -> intents.dispatch(new ListView.EditRequested(rowId))));
    }

    private Definition renderDeleteButton(String modulePath,
                                          String rowId,
                                          boolean disabled,
                                          IntentDispatcher<ListView.ListIntent> intents) {
        ElementRef dialogRef = createElementRef();
        return of(
                button(
                        attr("type", "button"),
                        attr("class", "btn-delete grid-row-delete"),
                        attr("aria-label", "Delete row " + rowId),
                        disabled ? attr("disabled", "disabled") : of(),
                        text("Delete"),
                        disabled ? of() : on("click", context -> context.showModal(dialogRef))),
                ConfirmationDialog.render(
                        modulePath + ":row-delete:" + rowId, dialogRef,
                        ConfirmationDialog.Spec.danger(
                                "Delete this item?",
                                "Item " + rowId + " will be permanently deleted.",
                                "Delete item"),
                        intents, new ListView.DeleteConfirmed(rowId)));
    }

    private Definition renderPagination(String position,
                                        ListView.ListViewState state,
                                        IntentDispatcher<ListView.ListIntent> intents) {
        int totalPages = state.totalPages();
        int displayedPage = totalPages == 0 ? 1 : state.page();
        ElementRef sizeRef = createElementRef();
        List<Integer> pageSizes = new ArrayList<>(STANDARD_PAGE_SIZES);
        if (!pageSizes.contains(state.pageSize())) {
            pageSizes.add(state.pageSize());
            pageSizes.sort(Integer::compareTo);
        }
        return nav(
                attr("class", "pagination pagination-" + position),
                attr("aria-label", position.equals("top") ? "Pagination above table" : "Pagination below table"),
                div(
                        attr("class", "pagination-summary"),
                        attr("aria-live", "polite"),
                        text(state.totalItems() == 0 ? "0 items"
                                : state.firstVisibleItem() + "–" + state.lastVisibleItem()
                                + " of " + state.totalItems())),
                div(
                        attr("class", "pagination-buttons"),
                        pageButton("First", 1, state.isBusy() || !state.hasPrevious(), intents),
                        pageButton("← Previous", state.page() - 1, state.isBusy() || !state.hasPrevious(), intents),
                        span(attr("class", "pagination-page"), text("Page " + displayedPage + " of " + Math.max(1, totalPages))),
                        pageButton("Next →", state.page() + 1, state.isBusy() || !state.hasNext(), intents),
                        pageButton("Last", Math.max(1, totalPages), state.isBusy() || !state.hasNext(), intents)),
                label(
                        attr("class", "page-size-control"),
                        text("Rows per page"),
                        select(
                                ref(sizeRef),
                                attr("aria-label", "Rows per page"),
                                state.isBusy() ? attr("disabled", "disabled") : of(),
                                of(pageSizes.stream().map(size -> option(
                                        attr("value", String.valueOf(size)),
                                        size == state.pageSize() ? attr("selected", "selected") : of(),
                                        text(String.valueOf(size))))),
                                state.isBusy() ? of() : on("change", context -> stringProperty(context, sizeRef).thenAccept(value -> {
                                    try {
                                        intents.dispatch(new ListView.PageSizeRequested(Integer.parseInt(value)));
                                    } catch (NumberFormatException ignored) {
                                        // Ignore values that were not rendered by this view.
                                    }
                                }))))
        );
    }

    private Definition pageButton(String label,
                                  int page,
                                  boolean disabled,
                                  IntentDispatcher<ListView.ListIntent> intents) {
        return button(
                attr("type", "button"),
                disabled ? attr("disabled", "disabled") : of(),
                text(label),
                disabled ? of() : on("click", _ -> intents.dispatch(new ListView.PageRequested(page)))
        );
    }

    private Definition renderValue(Object value, FieldDef field, ColumnConfig config) {
        if (value == null) return text("—");
        if (config.formatter() != null) {
            return text(config.formatter().apply(value));
        }
        String format = field.options().format();
        if (value instanceof LocalDate date) {
            return text(date.format(format == null ? DateTimeFormatter.ISO_LOCAL_DATE : DateTimeFormatter.ofPattern(format)));
        }
        if (value instanceof LocalDateTime dateTime) {
            return text(dateTime.format(format == null ? DateTimeFormatter.ISO_LOCAL_DATE_TIME : DateTimeFormatter.ofPattern(format)));
        }
        if (value instanceof Boolean bool) return text(bool ? "Yes" : "No");
        if (value instanceof Integer || value instanceof Long) return text(String.format("%,d", ((Number) value).longValue()));
        if (value instanceof Double || value instanceof Float) return text(String.format("%.2f", ((Number) value).doubleValue()));
        return text(value.toString());
    }

    private static String rowId(Map<String, Object> row, String rowKey) {
        Object id = row.get(rowKey);
        return id == null ? "" : String.valueOf(id);
    }

    private static String columnClass(ColumnConfig config) {
        TextAlign align = config.align();
        return switch (align) {
            case CENTER -> "text-center";
            case RIGHT -> "text-right";
            case LEFT -> "text-left";
        };
    }

    private String buildReturnQuery(ListQuery query) {
        List<String> values = new ArrayList<>();
        Map<String, String> original = new LinkedHashMap<>();
        if (query.page() > 1) original.put("p", String.valueOf(query.page()));
        original.put("size", String.valueOf(query.pageSize()));
        if (query.sort() != null) {
            original.put("sort", query.sort().field());
            original.put("dir", query.sort().direction().queryValue());
        }
        if (!query.search().isBlank()) original.put("q", query.search());
        query.filters().forEach((field, value) -> original.put("filter." + field, value));
        if (!original.isEmpty()) {
            String nested = original.entrySet().stream()
                    .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining("&"));
            values.add("fromQuery=" + encode(nested));
        }
        return String.join("&", values);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
