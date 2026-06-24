package rsp.compositions.dashboard;

import rsp.component.ComponentContext;
import rsp.component.ContextKey;
import rsp.component.Lookup;
import rsp.compositions.contract.ContractMetadata;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.ViewContract;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DashboardContract extends ViewContract {

    public static final ContextKey.StringKey<DashboardModel> DASHBOARD_MODEL =
            new ContextKey.StringKey<>("dashboard.model", DashboardModel.class);

    private final DashboardModel model;

    public DashboardContract(final Lookup lookup, final DashboardModel model) {
        super(lookup);
        this.model = Objects.requireNonNull(model);
    }

    @Override
    public ComponentContext enrichContext(final ComponentContext context) {
        return context
                .with(ContextKeys.CONTRACT_CLASS, getClass())
                .with(ContextKeys.CONTRACT_TITLE, title())
                .with(DASHBOARD_MODEL, model);
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
