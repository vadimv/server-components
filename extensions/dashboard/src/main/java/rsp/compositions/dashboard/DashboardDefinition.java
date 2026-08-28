package rsp.compositions.dashboard;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A serializable-style, immutable dashboard description. */
public record DashboardDefinition(String id,
                                  String title,
                                  GridDefinition grid,
                                  List<PlacedWidget> widgets) {
    public DashboardDefinition {
        requireText(id, "Dashboard id");
        requireText(title, "Dashboard title");
        Objects.requireNonNull(grid, "grid");
        widgets = widgets == null ? List.of() : List.copyOf(widgets);
        validateWidgets(grid, widgets);
    }

    private static void validateWidgets(GridDefinition grid, List<PlacedWidget> widgets) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < widgets.size(); i++) {
            PlacedWidget placement = Objects.requireNonNull(widgets.get(i), "widget placement");
            WidgetDefinition widget = placement.widget();
            requireText(widget.id(), "Dashboard widget id");
            requireText(widget.title(), "Dashboard widget title");
            requireText(widget.kind(), "Dashboard widget kind");
            if (!ids.add(widget.id())) {
                throw new IllegalArgumentException("Duplicate dashboard widget id: " + widget.id());
            }
            if (placement.area().column() + placement.area().columnSpan() - 1 > grid.columns()) {
                throw new IllegalArgumentException(
                        "Dashboard widget '" + widget.id() + "' extends past configured columns");
            }
            for (int j = 0; j < i; j++) {
                if (overlaps(placement.area(), widgets.get(j).area())) {
                    throw new IllegalArgumentException(
                            "Dashboard widget '" + widget.id() + "' overlaps '"
                                    + widgets.get(j).widget().id() + "'");
                }
            }
        }
    }

    private static boolean overlaps(GridArea left, GridArea right) {
        int leftEndColumn = left.column() + left.columnSpan() - 1;
        int rightEndColumn = right.column() + right.columnSpan() - 1;
        int leftEndRow = left.row() + left.rowSpan() - 1;
        int rightEndRow = right.row() + right.rowSpan() - 1;
        return left.column() <= rightEndColumn && right.column() <= leftEndColumn
                && left.row() <= rightEndRow && right.row() <= leftEndRow;
    }

    static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
