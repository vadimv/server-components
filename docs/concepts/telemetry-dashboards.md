# Telemetry Dashboards

The `telemetry` and `dashboard` extensions separate three concerns that tend to
become tangled in monitoring UIs:

```text
transport adapter -> TelemetryRegistry -> DashboardRuntime
                                            |
DashboardDefinition -> WidgetRendererRegistry -> Component
```

A dashboard definition is immutable configuration. It contains grid settings,
widget definitions, and typed `TelemetryKey` references; it never contains a
component, service, subscription, or callback. That makes definitions suitable
for validation, metadata inspection, storage, or a future text DSL.

## Dashboard DSL

```java
import static rsp.compositions.dashboard.DashboardDsl.*;

TelemetryKey<Double> pressure =
        TelemetryKey.of("plant.pressure", Double.class, "bar");

DashboardDefinition operations = dashboard("operations", "Operations")
        .columns(12)
        .rowHeightPx(96)
        .gap("1rem")
        .place(gauge("pressure")
                        .title("Line pressure")
                        .value(pressure)
                        .range(0, 16)
                        .staleAfter(Duration.ofSeconds(10)),
                at(1, 1).span(4, 2))
        .place(trend("pressure-history")
                        .title("Pressure history")
                        .series(pressure)
                        .window(Duration.ofMinutes(15)),
                at(5, 1).span(8, 3))
        .build();
```

The builder validates IDs, coordinates, column bounds, duplicate IDs, and
overlap. Built-in definitions currently cover value, gauge, and trend widgets.

## Telemetry Boundary

`Telemetry<T>` represents a scalar source and `TelemetrySeries<T>` an ordered
window. Both expose an immutable snapshot and a push subscription. Each
`TelemetrySample<T>` carries source time and `TelemetryQuality`; staleness is
computed with a caller-supplied `Clock`, which keeps tests deterministic.

Adapters own protocol details. An MQTT, OPC UA, WebSocket, polling, or in-memory
producer maps its values into telemetry samples and registers them:

```java
TelemetryRegistry telemetry = MapTelemetryRegistry.builder()
        .bind(pressure, pressureScalarAdapter)
        .bindSeries(pressure, pressureHistoryAdapter)
        .build();
```

Operational writes do not masquerade as telemetry reads. Use `CommandKey<C>`
and `CommandGateway` as a separate, authorization-aware boundary.

## Runtime And Custom Widgets

`DashboardRuntime` combines the telemetry registry, renderer registry, and
clock. `DashboardBlock` then joins the immutable definition to those runtime
dependencies:

```java
DashboardRuntime runtime = DashboardRuntime.defaults(telemetry);
Group monitoring = new Group("Monitoring")
        .bind(DashboardBlock.class, () -> new DashboardBlock(operations, runtime));
```

Applications can add domain widgets without weakening the definition boundary:
implement `WidgetDefinition`, implement a `WidgetRenderer<D>`, and register the
renderer beside the defaults. The definition remains data; the renderer is the
only layer allowed to create a live component and resolve services.

This fits the composition hierarchy directly:

- `Group` assembles the dashboard block with the rest of the application.
- `DashboardBlock` owns page-level state and metadata.
- Widget components own high-frequency telemetry subscriptions and state.
- Views render snapshots and dispatch intents without owning effects.
