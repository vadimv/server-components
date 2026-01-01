package rsp.compositions.ui;

import rsp.component.ComponentView;
import rsp.compositions.ListView;
import rsp.compositions.ListSchema;
import rsp.dsl.Definition;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static rsp.dsl.Html.*;

/**
 * DefaultListView - Adaptive list view implementation.
 * <p>
 * Renders ANY list data based on schema metadata.
 * Supports any number of columns and types.
 */
public class DefaultListView extends ListView {

    @Override
    public ComponentView<ListViewState> componentView() {
        return _ -> state -> {
            return div(
                h1(text("Items List")),
                table(
                    thead(
                        tr(
                            // Render headers dynamically based on schema
                            of(state.schema().columns().stream()
                                .map(col -> th(text(col.displayName())))
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
                )
            );
        };
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
