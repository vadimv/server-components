package rsp.compositions.dashboard;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.compositions.contract.ContractMetadata;
import rsp.compositions.contract.ContractNodeComponent;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DashboardContract extends ContractNodeComponent<DashboardView.DashboardState, Object> {

    private final DashboardModel model;

    public DashboardContract(final DashboardModel model) {
        this.model = Objects.requireNonNull(model);
    }

    @Override
    public ComponentStateSupplier<DashboardView.DashboardState> initStateSupplier() {
        return (_, _) -> new DashboardView.DashboardState(model);
    }

    @Override
    public ComponentView<DashboardView.DashboardState, Object> componentView() {
        return new DashboardView();
    }

    @Override
    public ContractMetadata contractMetadata() {
        DashboardLayout layout = model.layout();
        return new ContractMetadata(title(),
                "Grid dashboard containing self-contained monitoring widgets",
                null,
                Map.of("columns", layout.columns(),
                        "rowHeightPx", layout.rowHeightPx(),
                        "gap", layout.gap(),
                        "widgets", widgetMetadata(layout)));
    }

    @Override
    public String title() {
        return "Dashboard";
    }

    private static List<Map<String, Object>> widgetMetadata(final DashboardLayout layout) {
        return layout.placements().stream()
                .map(DashboardContract::widgetMetadata)
                .toList();
    }

    private static Map<String, Object> widgetMetadata(final WidgetPlacement placement) {
        DashboardWidget widget = placement.widget();
        GridArea area = placement.area();
        Map<String, Object> widgetState = widget.metadataState() == null
                ? Map.of()
                : Map.copyOf(widget.metadataState());
        return Map.of("id", widget.id(),
                "title", widget.title(),
                "kind", widget.kind(),
                "description", widget.description(),
                "grid", Map.of("column", area.column(),
                        "row", area.row(),
                        "columnSpan", area.columnSpan(),
                        "rowSpan", area.rowSpan()),
                "state", widgetState);
    }
}
