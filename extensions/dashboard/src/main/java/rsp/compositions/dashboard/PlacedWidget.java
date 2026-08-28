package rsp.compositions.dashboard;

import java.util.Objects;

/** A widget definition placed in a dashboard grid. */
public record PlacedWidget(WidgetDefinition widget, GridArea area) {
    public PlacedWidget {
        Objects.requireNonNull(widget, "widget");
        Objects.requireNonNull(area, "area");
    }
}
