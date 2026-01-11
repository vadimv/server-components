package rsp.compositions.ui;

import rsp.component.ComponentView;
import rsp.component.definitions.ContextStateComponent;
import rsp.compositions.EditMode;
import rsp.compositions.ListView;
import rsp.dsl.Definition;
import rsp.page.events.ComponentEventNotification;

import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static rsp.dsl.Html.*;

/**
 * DefaultListView - Adaptive list view implementation.
 * <p>
 * Renders ANY list data based on schema metadata.
 * Supports any number of columns and types.
 * Includes pagination and sorting interactivity.
 */
public class DefaultListView extends ListView {

    @Override
    public ComponentView<ListViewState> componentView() {
        return newState -> state -> {
            final int page = state.page();
            final String sort = state.sort();
            final String modulePath = state.modulePath();
            final EditMode editMode = state.editMode();
            final String createToken = state.createToken();

            // Capture current query params for Edit links
            final String currentQueryParams = buildQueryString(page, sort);

            return div(
                h1(text("Items List")),

                // Create New action - varies by edit mode
                div(attr("class", "list-actions"),
                    renderCreateButton(modulePath, editMode, createToken)
                ),

                // Pagination controls at top
                div(attr("class", "pagination-top"),
                    renderPaginationControls(page)
                ),

                // Table with sortable headers
                table(
                    thead(
                        tr(
                            // Render headers dynamically with sort links
                            of(state.schema().columns().stream()
                                .map(col -> th(
                                    a(
                                        attr("href", "#"),
                                        text(col.displayName() + " " + getSortIndicator(sort)),
                                        on("click", ctx -> {
                                            // Toggle sort direction
                                            String newSort = sort.equals("asc") ? "desc" : "asc";

                                            // Send event to AddressBarSyncComponent
                                            commandsEnqueue.accept(new ComponentEventNotification(
                                                "stateUpdated.sort",
                                                new ContextStateComponent.ContextValue.StringValue(newSort)
                                            ));
                                        })
                                    )
                                ))
                            ),
                            // NEW: Actions column header
                            th(text("Actions"))
                        )
                    ),
                    tbody(
                        // Render rows dynamically with Edit link
                        of(state.rows().stream().map(row ->
                            tr(
                                // Existing data columns
                                of(state.schema().columns().stream()
                                    .map(col -> td(renderValue(row.get(col.name()), col.type())))
                                ),
                                // NEW: Actions column with Edit link
                                td(
                                    a(
                                        attr("href", buildEditUrl(row, currentQueryParams, modulePath)),
                                        text("Edit")
                                    )
                                )
                            )
                        ))
                    )
                ),

                // Pagination controls at bottom
                div(attr("class", "pagination-bottom"),
                    renderPaginationControls(page)
                )
            );
        };
    }

    /**
     * Render the Create button based on edit mode.
     * <p>
     * - SEPARATE_PAGE: Link to /modulePath/{createToken}
     * - QUERY_PARAM: Link with ?create=true query param
     * - MODAL: Button that emits openCreateModal event
     */
    private Definition renderCreateButton(String modulePath, EditMode editMode, String createToken) {
        return switch (editMode) {
            case SEPARATE_PAGE -> a(
                    attr("href", modulePath + "/" + createToken),
                    attr("class", "create-button"),
                    text("Create New")
            );
            case QUERY_PARAM -> a(
                    attr("href", modulePath + "?create=true"),
                    attr("class", "create-button"),
                    text("Create New")
            );
            case MODAL -> button(
                    attr("type", "button"),
                    attr("class", "create-button"),
                    text("Create New"),
                    on("click", ctx -> {
                        commandsEnqueue.accept(new ComponentEventNotification("openCreateModal", Map.of()));
                    })
            );
        };
    }

    /**
     * Render pagination controls (Previous/Next buttons).
     */
    private Definition renderPaginationControls(int currentPage) {
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
                        commandsEnqueue.accept(new ComponentEventNotification(
                            "stateUpdated.p",
                            new ContextStateComponent.ContextValue.StringValue(String.valueOf(currentPage - 1))
                        ));
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
                    // Send event to AddressBarSyncComponent
                    commandsEnqueue.accept(new ComponentEventNotification(
                        "stateUpdated.p",
                        new ContextStateComponent.ContextValue.StringValue(String.valueOf(currentPage + 1))
                    ));
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

    /**
     * Build edit URL for a specific row, preserving list query params.
     *
     * @param row The row data map
     * @param queryParams Preserved query params from list view (e.g., "fromP=3&fromSort=desc")
     * @param modulePath The module base path (e.g., "/posts")
     * @return Edit URL with preserved params (e.g., "/posts/123?fromP=3&fromSort=desc")
     */
    private String buildEditUrl(java.util.Map<String, Object> row, String queryParams, String modulePath) {
        // Get entity ID from row (assumes "id" field exists)
        Object id = row.get("id");
        if (id == null) {
            throw new IllegalStateException("Row must have 'id' field for edit link");
        }

        String editPath = modulePath + "/" + id;

        if (queryParams.isEmpty()) {
            return editPath;
        }

        return editPath + "?" + queryParams;
    }
}
