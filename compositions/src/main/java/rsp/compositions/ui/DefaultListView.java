package rsp.compositions.ui;

import rsp.component.ComponentView;
import rsp.component.definitions.ContextStateComponent;
import rsp.compositions.ListView;
import rsp.dsl.Definition;
import rsp.page.events.ComponentEventNotification;

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

            return div(
                h1(text("Items List")),

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
                            )
                        )
                    ),
                    tbody(
                        // Render rows dynamically based on schema
                        of(state.rows().stream().map(row ->
                            tr(
                                of(state.schema().columns().stream()
                                    .map(col -> td(renderValue(row.get(col.name()), col.type())))
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
}
