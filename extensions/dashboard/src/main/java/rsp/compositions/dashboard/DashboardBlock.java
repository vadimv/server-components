package rsp.compositions.dashboard;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.compositions.block.Block;
import rsp.compositions.block.BlockMetadata;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DashboardBlock extends Block<DashboardView.DashboardState, Object> {

    private final DashboardDefinition definition;
    private final DashboardRuntime runtime;

    public DashboardBlock(DashboardDefinition definition, DashboardRuntime runtime) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public ComponentStateSupplier<DashboardView.DashboardState> initStateSupplier() {
        return (_, _) -> new DashboardView.DashboardState(definition);
    }

    @Override
    public ComponentView<DashboardView.DashboardState, Object> componentView() {
        return new DashboardView(runtime);
    }

    @Override
    public BlockMetadata blockMetadata() {
        GridDefinition grid = definition.grid();
        return new BlockMetadata(title(),
                "Telemetry dashboard assembled from immutable widget definitions",
                null,
                Map.of("id", definition.id(),
                        "columns", grid.columns(),
                        "rowHeightPx", grid.rowHeightPx(),
                        "gap", grid.gap(),
                        "widgets", widgetMetadata(definition.widgets())));
    }

    @Override
    public String title() {
        return definition.title();
    }

    private static List<Map<String, Object>> widgetMetadata(List<PlacedWidget> widgets) {
        return widgets.stream()
                .map(DashboardBlock::widgetMetadata)
                .toList();
    }

    private static Map<String, Object> widgetMetadata(PlacedWidget placement) {
        WidgetDefinition widget = placement.widget();
        GridArea area = placement.area();
        return Map.of("id", widget.id(),
                "title", widget.title(),
                "kind", widget.kind(),
                "description", widget.description(),
                "grid", Map.of("column", area.column(),
                        "row", area.row(),
                        "columnSpan", area.columnSpan(),
                        "rowSpan", area.rowSpan()),
                "definition", Map.copyOf(widget.metadata()));
    }
}
