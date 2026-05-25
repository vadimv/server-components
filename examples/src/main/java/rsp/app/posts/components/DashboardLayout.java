package rsp.app.posts.components;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record DashboardLayout(int columns,
                              int rowHeightPx,
                              String gap,
                              List<WidgetPlacement> placements) {
    public DashboardLayout {
        if (columns < 1) {
            throw new IllegalArgumentException("Dashboard grid must have at least one column.");
        }
        if (rowHeightPx < 1) {
            throw new IllegalArgumentException("Dashboard row height must be 1px or greater.");
        }
        gap = Objects.requireNonNull(gap, "gap");
        if (gap.isBlank()) {
            throw new IllegalArgumentException("Dashboard grid gap must not be blank.");
        }
        placements = placements == null ? List.of() : List.copyOf(placements);
        validatePlacements(columns, placements);
    }

    private static void validatePlacements(final int columns,
                                           final List<WidgetPlacement> placements) {
        Set<String> widgetIds = new HashSet<>();
        for (WidgetPlacement placement : placements) {
            Objects.requireNonNull(placement, "placement");
            String widgetId = placement.widget().id();
            if (widgetId == null || widgetId.isBlank()) {
                throw new IllegalArgumentException("Dashboard widget id must not be blank.");
            }
            if (!widgetIds.add(widgetId)) {
                throw new IllegalArgumentException("Duplicate dashboard widget id: " + widgetId);
            }

            GridArea area = placement.area();
            int endColumn = area.column() + area.columnSpan() - 1;
            if (endColumn > columns) {
                throw new IllegalArgumentException(
                        "Dashboard widget '" + widgetId + "' extends past configured columns.");
            }
        }
    }
}
