package rsp.app.posts.components;

import java.util.ArrayList;
import java.util.List;

public final class DashboardDsl {
    private DashboardDsl() {}

    public static Builder dashboard() {
        return new Builder();
    }

    public static GridAreaBuilder at(final int column, final int row) {
        return new GridAreaBuilder(column, row);
    }

    public static final class Builder {
        private int columns = 12;
        private int rowHeightPx = 96;
        private String gap = "1rem";
        private final List<WidgetPlacement> placements = new ArrayList<>();

        public Builder columns(final int columns) {
            this.columns = columns;
            return this;
        }

        public Builder rowHeightPx(final int rowHeightPx) {
            this.rowHeightPx = rowHeightPx;
            return this;
        }

        public Builder gap(final String gap) {
            this.gap = gap;
            return this;
        }

        public Builder place(final DashboardWidget widget, final GridArea area) {
            placements.add(new WidgetPlacement(widget, area));
            return this;
        }

        public DashboardLayout build() {
            return new DashboardLayout(columns, rowHeightPx, gap, placements);
        }
    }

    public record GridAreaBuilder(int column, int row) {
        public GridArea span(final int columnSpan, final int rowSpan) {
            return new GridArea(column, row, columnSpan, rowSpan);
        }
    }
}
