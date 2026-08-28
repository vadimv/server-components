package rsp.compositions.dashboard;

import rsp.telemetry.TelemetryRegistry;

import java.time.Clock;
import java.util.Objects;

/** Runtime dependencies kept separate from immutable dashboard definitions. */
public record DashboardRuntime(TelemetryRegistry telemetry,
                               WidgetRendererRegistry renderers,
                               Clock clock) {
    public DashboardRuntime {
        Objects.requireNonNull(telemetry, "telemetry");
        Objects.requireNonNull(renderers, "renderers");
        Objects.requireNonNull(clock, "clock");
    }

    public static DashboardRuntime defaults(TelemetryRegistry telemetry) {
        return new DashboardRuntime(telemetry, WidgetRendererRegistry.defaults(), Clock.systemUTC());
    }
}
