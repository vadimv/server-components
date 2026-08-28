package rsp.compositions.dashboard;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;
import rsp.dsl.Definition;

import static rsp.dsl.Html.*;

public class DashboardGrid extends Component<DashboardDefinition, Object> {

    private final DashboardDefinition definition;
    private final DashboardRuntime runtime;

    public DashboardGrid(DashboardDefinition definition, DashboardRuntime runtime) {
        this.definition = java.util.Objects.requireNonNull(definition, "definition");
        this.runtime = java.util.Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public ComponentStateSupplier<DashboardDefinition> initStateSupplier() {
        return (_, _) -> definition;
    }

    @Override
    public ComponentView<DashboardDefinition, Object> componentView() {
        return _ -> state -> div(
                attr("class", "dashboard-grid"),
                attr("style", gridStyle(state.grid())),
                of(state.widgets().stream().map(this::renderPlacement))
        );
    }

    private Definition renderPlacement(PlacedWidget placement) {
        return div(
                attr("class", "dashboard-grid-item"),
                attr("data-widget-id", placement.widget().id()),
                attr("style", placementStyle(placement.area())),
                runtime.renderers().render(placement.widget(), runtime)
        );
    }

    private static String gridStyle(GridDefinition grid) {
        return "--dashboard-columns: " + grid.columns()
                + "; --dashboard-row-height: " + grid.rowHeightPx() + "px"
                + "; --dashboard-gap: " + grid.gap();
    }

    private static String placementStyle(final GridArea area) {
        return "grid-column: " + area.column() + " / span " + area.columnSpan()
                + "; grid-row: " + area.row() + " / span " + area.rowSpan();
    }
}
