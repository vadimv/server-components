package rsp.app.posts.components;

import rsp.compositions.dashboard.DashboardDefinition;
import rsp.compositions.dashboard.DashboardRuntime;
import rsp.compositions.dashboard.WidgetRendererRegistry;
import rsp.telemetry.TelemetryRegistry;

import java.time.Duration;

import static rsp.compositions.dashboard.DashboardDsl.at;
import static rsp.compositions.dashboard.DashboardDsl.dashboard;
import static rsp.compositions.dashboard.DashboardDsl.trend;

/**
 * App-specific immutable dashboard definition and runtime renderer wiring.
 */
public final class DemoDashboards {

    private DemoDashboards() {}

    public static DashboardDefinition definition() {
        return dashboard("admin-overview", "Dashboard")
                .columns(12)
                .rowHeightPx(96)
                .gap("1.5rem")
                .place(trend("comments-rate")
                                .title("Comments rate")
                                .description("Live comments per second stream")
                                .series(DemoTelemetry.COMMENTS_RATE)
                                .window(Duration.ofSeconds(30))
                                .decimals(0),
                        at(1, 1).span(6, 3))
                .place(new LogsDefinition("logs", "Logs", "Live application log stream",
                                DemoTelemetry.LOG_ENTRIES),
                        at(1, 4).span(10, 3))
                .build();
    }

    public static DashboardRuntime runtime(TelemetryRegistry telemetry) {
        return new DashboardRuntime(
                telemetry,
                WidgetRendererRegistry.builder()
                        .registerDefaults()
                        .register(LogsWidget.renderer())
                        .build(),
                java.time.Clock.systemUTC());
    }
}
