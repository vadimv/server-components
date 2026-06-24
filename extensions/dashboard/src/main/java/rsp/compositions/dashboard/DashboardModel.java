package rsp.compositions.dashboard;

/**
 * Immutable dashboard description: the grid {@link DashboardLayout} plus its widget placements.
 * <p>
 * Concrete dashboards (demo data, live data wiring) are assembled by the consuming application
 * via {@link DashboardDsl}; this extension is intentionally free of any app-specific widgets.
 */
public record DashboardModel(DashboardLayout layout) {

    public DashboardModel {
        layout = layout == null ? DashboardDsl.dashboard().build() : layout;
    }
}
