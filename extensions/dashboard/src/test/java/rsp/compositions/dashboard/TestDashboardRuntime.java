package rsp.compositions.dashboard;

import rsp.telemetry.MapTelemetryRegistry;
import rsp.telemetry.TelemetryKey;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

final class TestDashboardRuntime {
    static final TelemetryKey<Double> SIGNAL =
            TelemetryKey.of("test.signal", Double.class, "units");

    private TestDashboardRuntime() {}

    static DashboardRuntime customWidgets() {
        return new DashboardRuntime(
                MapTelemetryRegistry.builder().build(),
                WidgetRendererRegistry.builder().register(TestWidgetDefinition.renderer()).build(),
                Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC));
    }
}
