package rsp.compositions.ui;

import rsp.component.ComponentView;
import rsp.component.CommandsEnqueue;
import rsp.component.StateUpdate;
import rsp.component.Subscriber;
import rsp.component.definitions.ContextStateComponent;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.ListViewContract;
import rsp.compositions.contract.ListView;
import rsp.dsl.Definition;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static rsp.compositions.contract.EventKeys.*;
import static rsp.compositions.contract.ListViewContract.*;
import static rsp.dsl.Html.*;

/**
 * DefaultListView - Adaptive list view implementation.
 * <p>
 * Renders ANY list data based on schema metadata.
 * Supports any number of columns and types.
 * Includes pagination and sorting interactivity.
 * <p>
 * Create/Edit actions trigger events that open overlay contracts via SHOW events.
 */
public class DefaultListView extends ListView {

    private final System.Logger logger = System.getLogger(getClass().getName());

    @Override
    public void onAfterRendered(ListViewState state,
                                Subscriber subscriber,
                                CommandsEnqueue commandsEnqueue,
                                StateUpdate<ListViewState> stateUpdate) {
        subscriber.addEventHandler(ListViewContract.SELECT_ALL_REQUESTED, () -> {
            stateUpdate.applyStateTransformation(s -> {
                ListViewState newState = s.selectAll();
                lookup.publish(ListViewContract.SELECTION_CHANGED,
                        new ListViewContract.SelectedItems(newState.selectedIds()));
                return newState;
            });
        }, false);
    }

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
                h1(text(state.title())),

                // Create New action - triggers event to open overlay
                div(attr("class", "list-actions"),
                    renderCreateButton(),
                    // Bulk delete button (only when selectable and has selections)
                    selectable && !state.selectedIds().isEmpty()
                        ? renderBulkDeleteButton(state, newState)
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
                                            ListViewState updated;
                                            if (state.isAllSelected()) {
                                                updated = state.clearSelection();
                                            } else {
                                                updated = state.selectAll();
                                            }
                                            newState.setState(updated);
                                            lookup.publish(ListViewContract.SELECTION_CHANGED,
                                                    new ListViewContract.SelectedItems(updated.selectedIds()));
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
                                                ListViewState updated = state.toggleSelection(rowId);
                                                newState.setState(updated);
                                                lookup.publish(ListViewContract.SELECTION_CHANGED,
                                                        new ListViewContract.SelectedItems(updated.selectedIds()));
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
                                    renderEditButton(rowId, currentQueryParams)
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
     * Clears selection immediately after confirming delete (optimistic UI update).
     */
    private Definition renderBulkDeleteButton(ListViewState state,
                                              rsp.component.StateUpdate<ListViewState> stateUpdate) {
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
                            // Clear selection immediately (optimistic UI update)
                            // This hides the button and unchecks all checkboxes
                            ListViewState cleared = state.clearSelection();
                            stateUpdate.setState(cleared);
                            lookup.publish(ListViewContract.SELECTION_CHANGED,
                                    new ListViewContract.SelectedItems(cleared.selectedIds()));
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
     * Emits abstract ACTION("create") event - contract translates to SHOW via actionBindings().
     */
    private Definition renderCreateButton() {
        return button(
            attr("type", "button"),
            attr("class", "create-button"),
            text("Create New"),
            on("click", ctx -> {
                lookup.publish(CREATE_ELEMENT_REQUESTED);
            }));
    }

    /**
     * Render Edit button/link for a row.
     * <p>
     * Behavior depends on edit contract's route configuration:
     * <ul>
     *   <li>Primary-like (no parent route) + has route → navigate via link (full page edit)</li>
     *   <li>Overlay-like (has parent route) or no route → event (SHOW-based overlay)</li>
     * </ul>
     *
     * @param rowId The row ID for the edit link
     * @param queryParams Query params to preserve (e.g., "fromP=2&fromSort=desc")
     */
    private Definition renderEditButton(String rowId, String queryParams) {
        Boolean editHasRoute = lookup.get(ContextKeys.EDIT_HAS_ROUTE);
        Boolean editOpensAsOverlay = lookup.get(ContextKeys.EDIT_OPENS_AS_OVERLAY);
        String editRoutePattern = lookup.get(ContextKeys.EDIT_ROUTE_PATTERN);

        // Primary-like with route → navigate via link
        if (editHasRoute != null && editHasRoute
                && (editOpensAsOverlay == null || !editOpensAsOverlay)) {
            String editUrl = buildEditUrl(editRoutePattern, rowId, queryParams);
            return a(
                attr("href", editUrl),
                attr("class", "edit-button edit-link"),
                text("Edit")
            );
        }

        // Overlay-like or no route → trigger event (LayerComponent handles SHOW)
        return button(
            attr("type", "button"),
            attr("class", "edit-button"),
            text("Edit"),
            on("click", ctx -> {
                lookup.publish(EDIT_ELEMENT_REQUESTED, rowId);
            })
        );
    }

    /**
     * Build edit URL by replacing :id placeholder in pattern and appending query params.
     *
     * @param pattern The route pattern (e.g., "/posts/:id")
     * @param entityId The entity ID to substitute
     * @param queryParams Query params to append (e.g., "fromP=2")
     * @return The complete edit URL (e.g., "/posts/10?fromP=2")
     */
    private String buildEditUrl(String pattern, String entityId, String queryParams) {
        if (pattern == null) return "#";
        String url = pattern.replace(":id", entityId);
        if (queryParams != null && !queryParams.isEmpty()) {
            url += "?" + queryParams;
        }
        return url;
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
                        ListViewState cleared = state.clearSelection();
                        newState.setState(cleared);
                        lookup.publish(ListViewContract.SELECTION_CHANGED,
                                new ListViewContract.SelectedItems(cleared.selectedIds()));
                        lookup.publish(PAGE_CHANGE_REQUESTED, currentPage - 1);
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
                    ListViewState cleared = state.clearSelection();
                    newState.setState(cleared);
                    lookup.publish(ListViewContract.SELECTION_CHANGED,
                            new ListViewContract.SelectedItems(cleared.selectedIds()));
                    lookup.publish(PAGE_CHANGE_REQUESTED, currentPage + 1);
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
