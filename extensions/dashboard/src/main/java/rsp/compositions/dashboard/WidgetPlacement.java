package rsp.compositions.dashboard;

import java.util.Objects;

public record WidgetPlacement(DashboardWidget widget, GridArea area) {
    public WidgetPlacement {
        Objects.requireNonNull(widget, "widget");
        Objects.requireNonNull(area, "area");
    }
}
