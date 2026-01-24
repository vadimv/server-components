package rsp.compositions.ui;

import rsp.component.ComponentView;
import rsp.component.definitions.ContextStateComponent;
import rsp.compositions.ListView;
import rsp.dsl.Definition;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static rsp.compositions.EventKeys.*;
import static rsp.dsl.Html.*;

/**
 * DefaultListView - Adaptive list view implementation.
 * <p>
 * Renders ANY list data based on schema metadata.
 * Supports any number of columns and types.
 * Includes pagination and sorting interactivity.
 * <p>
 * Create/Edit actions trigger events that open overlay contracts (Slot.OVERLAY).
 */
public class DefaultListView extends ListView {

    @Override
    public ComponentView<ListViewState> componentView() {
        return newState -> state -> {
            final int page = state.page();
            final String sort = state.sort();
            final String modulePath = state.modulePath();
            final boolean selectable = state.schema().selectable();

            // Capture current query params for Edit links
            final String currentQueryParams = buildQueryString(page, sort);

            return div(
                h1(text("Items List")),

                // Create New action - triggers event to open overlay
                div(attr("class", "list-actions"),
                    renderCreateButton(),
                    // Bulk delete button (only when selectable and has selections)
                    selectable && !state.selectedIds().isEmpty()
                        ? renderBulkDeleteButton(state)
                        : of()
                ),

                // Pagination controls at top
                div(attr("class", "pagination-top"),
                    renderPaginationControls(page, state, newState)
                ),

                // Table with sortable headers
                table(
                    thead(
                        tr(
                            // Checkbox column header (only when selectable)
                            selectable
                                ? th(
                                    input(
                                        attr("type", "checkbox"),
                                        state.isAllSelected() ? attr("checked", "checked") : of(),
                                        on("click", ctx -> {
                                            if (state.isAllSelected()) {
                                                newState.setState(state.clearSelection());
                                            } else {
                                                newState.setState(state.selectAll());
                                            }
                                        })
                                    )
                                  )
                                : of(),
                            // Render headers dynamically with sort links
                            of(state.schema().columns().stream()
                                .map(col -> th(
                                    a(
                                        attr("href", "#"),
                                        text(col.displayName() + " " + getSortIndicator(sort)),
                                        on("click", true, ctx -> {  // preventDefault to avoid # in URL
                                            // Toggle sort direction
                                            String newSort = sort.equals("asc") ? "desc" : "asc";

                                            // Send event to AddressBarSyncComponent
                                            lookup.publish(STATE_UPDATED.with("sort"),
                                                new ContextStateComponent.ContextValue.StringValue(newSort)
                                            );
                                        })
                                    )
                                ))
                            ),
                            // Actions column header
                            th(text("Actions"))
                        )
                    ),
                    tbody(
                        // Render rows dynamically with Edit link
                        of(state.rows().stream().map(row -> {
                            String rowId = getRowId(row);
                            return tr(
                                // Checkbox column (only when selectable)
                                selectable
                                    ? td(
                                        input(
                                            attr("type", "checkbox"),
                                            state.isSelected(rowId) ? attr("checked", "checked") : of(),
                                            on("click", ctx -> {
                                                newState.setState(state.toggleSelection(rowId));
                                            })
                                        )
                                      )
                                    : of(),
                                // Existing data columns
                                of(state.schema().columns().stream()
                                    .map(col -> td(renderValue(row.get(col.name()), col.type())))
                                ),
                                // Actions column with Edit button
                                td(
                                    renderEditButton(rowId, modulePath, currentQueryParams)
                                )
                            );
                        }))
                    )
                ),

                // Pagination controls at bottom
                div(attr("class", "pagination-bottom"),
                    renderPaginationControls(page, state, newState)
                )
            );
        };
    }

    /**
     * Render the bulk delete button.
     */
    private Definition renderBulkDeleteButton(ListViewState state) {
        int count = state.selectedIds().size();
        return button(
            attr("type", "button"),
            attr("class", "btn-delete btn-danger"),
            text("Delete Selected (" + count + ")"),
            on("click", ctx -> {
                ctx.evalJs("confirm('Are you sure you want to delete " + count + " items?')")
                    .thenAccept(result -> {
                        if (result instanceof rsp.util.json.JsonDataType.Boolean confirmed && confirmed.value()) {
                            lookup.publish(BULK_DELETE_REQUESTED, state.selectedIds());
                        }
                    });
            })
        );
    }

    /**
     * Get the row ID as a string.
     */
    private String getRowId(java.util.Map<String, Object> row) {
        Object id = row.get("id");
        return id != null ? String.valueOf(id) : "";
    }

    /**
     * Render the Create button.
     * Triggers OPEN_CREATE_MODAL event to open the overlay.
     */
    private Definition renderCreateButton() {
        return button(
            attr("type", "button"),
            attr("class", "create-button"),
            text("Create New"),
            on("click", ctx -> {
                lookup.publish(OPEN_CREATE_MODAL);
            })
        );
    }

    /**
     * Render Edit button for a row.
     * For now, uses link-based navigation (preserves query params).
     * Future: could trigger OPEN_EDIT_MODAL event for overlay editing.
     */
    private Definition renderEditButton(String rowId, String modulePath, String queryParams) {
        String editPath = modulePath + "/" + rowId;
        if (!queryParams.isEmpty()) {
            editPath += "?" + queryParams;
        }

        return a(
            attr("href", editPath),
            text("Edit")
        );
    }

    /**
     * Render pagination controls (Previous/Next buttons).
     * Clears selection when page changes.
     */
    private Definition renderPaginationControls(int currentPage, ListViewState state,
                                                  rsp.component.StateUpdate<ListViewState> newState) {
        return div(
            // Previous button - conditionally render based on page
            currentPage <= 1
                ? button(
                    attr("type", "button"),
                    attr("disabled", "disabled"),
                    text("← Previous")
                  )
                : button(
                    attr("type", "button"),
                    text("← Previous"),
                    on("click", ctx -> {
                        // Clear selection when changing page
                        newState.setState(state.clearSelection());
                        lookup.publish(STATE_UPDATED.with("p"),
                            new ContextStateComponent.ContextValue.StringValue(String.valueOf(currentPage - 1))
                        );
                    })
                  ),

            // Current page indicator
            span(
                attr("style", "margin: 0 1em;"),
                text("Page " + currentPage)
            ),

            // Next button
            button(
                attr("type", "button"),
                text("Next →"),
                on("click", ctx -> {
                    // Clear selection when changing page
                    newState.setState(state.clearSelection());
                    // Send event to AddressBarSyncComponent
                    lookup.publish(STATE_UPDATED.with("p"),
                        new ContextStateComponent.ContextValue.StringValue(String.valueOf(currentPage + 1))
                    );
                })
            )
        );
    }

    /**
     * Get sort direction indicator for column headers.
     */
    private String getSortIndicator(String sort) {
        return sort.equals("asc") ? "↑" : "↓";
    }

    /**
     * Render a value based on its type.
     * Provides type-aware formatting (dates, booleans, numbers, etc.)
     */
    private Definition renderValue(Object value, Class<?> type) {
        if (value == null) {
            return text("");
        }

        // Date formatting
        if (value instanceof LocalDate date) {
            return text(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
        if (value instanceof LocalDateTime dateTime) {
            return text(dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        // Boolean as Yes/No
        if (value instanceof Boolean bool) {
            return text(bool ? "Yes" : "No");
        }

        // Numbers with formatting
        if (value instanceof Number number) {
            return text(formatNumber(number));
        }

        // Default: toString
        return text(value.toString());
    }

    private String formatNumber(Number number) {
        // Simple formatting - could be enhanced with locale-aware formatting
        if (number instanceof Integer || number instanceof Long) {
            return String.format("%,d", number.longValue());
        }
        if (number instanceof Double || number instanceof Float) {
            return String.format("%.2f", number.doubleValue());
        }
        return number.toString();
    }

    /**
     * Build query string from current list view state.
     * Captures query params for preservation when navigating to edit view.
     *
     * @param page Current page number
     * @param sort Current sort direction
     * @return Query string (e.g., "fromP=3&fromSort=desc"), or empty string
     */
    private String buildQueryString(int page, String sort) {
        java.util.List<String> params = new java.util.ArrayList<>();

        // Preserve page if not on first page (use "fromP" to match url.query.p)
        if (page > 1) {
            params.add("fromP=" + page);
        }

        // Preserve sort if not default
        if (sort != null && !sort.isEmpty() && !sort.equals("asc")) {
            params.add("fromSort=" + sort);
        }

        return params.isEmpty() ? "" : String.join("&", params);
    }
}
