package rsp.app.posts.components;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;
import rsp.dsl.Definition;

import static rsp.dsl.Html.*;

public class DashboardGrid extends Component<DashboardLayout> {

    private final DashboardLayout layout;

    public DashboardGrid(final DashboardLayout layout) {
        this.layout = layout == null ? DashboardDsl.dashboard().build() : layout;
    }

    @Override
    public ComponentStateSupplier<DashboardLayout> initStateSupplier() {
        return (_, _) -> layout;
    }

    @Override
    public ComponentView<DashboardLayout> componentView() {
        return _ -> state -> div(
                attr("class", "dashboard-grid"),
                attr("style", gridStyle(state)),
                of(state.placements().stream().map(DashboardGrid::renderPlacement))
        );
    }

    private static Definition renderPlacement(final WidgetPlacement placement) {
        return div(
                attr("class", "dashboard-grid-item"),
                attr("data-widget-id", placement.widget().id()),
                attr("style", placementStyle(placement.area())),
                placement.widget().component()
        );
    }

    private static String gridStyle(final DashboardLayout layout) {
        return "--dashboard-columns: " + layout.columns()
                + "; --dashboard-row-height: " + layout.rowHeightPx() + "px"
                + "; --dashboard-gap: " + layout.gap();
    }

    private static String placementStyle(final GridArea area) {
        return "grid-column: " + area.column() + " / span " + area.columnSpan()
                + "; grid-row: " + area.row() + " / span " + area.rowSpan();
    }
}
