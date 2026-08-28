package rsp.compositions.dashboard;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.component.TreeBuilder;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.telemetry.MapTelemetryRegistry;
import rsp.telemetry.Subscription;
import rsp.telemetry.Telemetry;
import rsp.telemetry.TelemetrySample;
import rsp.telemetry.TelemetrySeries;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardTelemetryTests {
    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");

    @Test
    void default_renderers_resolve_typed_scalar_and_series_signals() {
        Telemetry<Double> scalar = scalar(TelemetrySample.good(42.5, NOW.minusSeconds(5)));
        TelemetrySeries<Double> series = series(List.of(
                TelemetrySample.good(20.0, NOW.minusSeconds(20)),
                TelemetrySample.good(30.0, NOW.minusSeconds(10)),
                TelemetrySample.good(25.0, NOW)));
        var telemetry = MapTelemetryRegistry.builder()
                .bind(TestDashboardRuntime.SIGNAL, scalar)
                .bindSeries(TestDashboardRuntime.SIGNAL, series)
                .build();
        DashboardRuntime runtime = new DashboardRuntime(telemetry,
                WidgetRendererRegistry.defaults(), Clock.fixed(NOW, ZoneOffset.UTC));
        DashboardDefinition definition = DashboardDsl.dashboard("telemetry", "Telemetry")
                .place(DashboardDsl.value("current")
                                .title("Current")
                                .value(TestDashboardRuntime.SIGNAL),
                        DashboardDsl.at(1, 1).span(3, 1))
                .place(DashboardDsl.gauge("level")
                                .title("Level")
                                .value(TestDashboardRuntime.SIGNAL)
                                .range(0, 100),
                        DashboardDsl.at(4, 1).span(3, 1))
                .place(DashboardDsl.trend("history")
                                .title("History")
                                .series(TestDashboardRuntime.SIGNAL)
                                .window(Duration.ofMinutes(1)),
                        DashboardDsl.at(1, 2).span(6, 2))
                .build();

        Document document = render(new DashboardBlock(definition, runtime));

        assertEquals("42.5", document.select(".value-widget .dashboard-widget-value").text());
        assertTrue(document.select(".gauge-fill").attr("style").contains("42.50%"));
        assertFalse(document.select(".telemetry-trend-chart path").attr("d").isBlank());
        assertEquals(3, document.select(".dashboard-grid-item").size());
    }

    @Test
    void value_renderer_marks_old_samples_stale_using_runtime_clock() {
        var telemetry = MapTelemetryRegistry.builder()
                .bind(TestDashboardRuntime.SIGNAL,
                        scalar(TelemetrySample.good(10.0, NOW.minusSeconds(31))))
                .build();
        DashboardRuntime runtime = new DashboardRuntime(telemetry,
                WidgetRendererRegistry.defaults(), Clock.fixed(NOW, ZoneOffset.UTC));
        DashboardDefinition definition = DashboardDsl.dashboard("telemetry", "Telemetry")
                .place(DashboardDsl.value("current")
                                .value(TestDashboardRuntime.SIGNAL)
                                .staleAfter(Duration.ofSeconds(30)),
                        DashboardDsl.at(1, 1).span(2, 1))
                .build();

        Document document = render(new DashboardBlock(definition, runtime));

        assertEquals(1, document.select(".value-widget.telemetry-stale").size());
    }

    private static Telemetry<Double> scalar(TelemetrySample<Double> sample) {
        return new Telemetry<>() {
            @Override
            public Optional<TelemetrySample<Double>> snapshot() {
                return Optional.of(sample);
            }

            @Override
            public Subscription subscribe(Consumer<TelemetrySample<Double>> subscriber) {
                return Subscription.none();
            }
        };
    }

    private static TelemetrySeries<Double> series(List<TelemetrySample<Double>> samples) {
        return new TelemetrySeries<>() {
            @Override
            public List<TelemetrySample<Double>> snapshot() {
                return samples;
            }

            @Override
            public Subscription subscribe(Consumer<List<TelemetrySample<Double>>> subscriber) {
                return Subscription.none();
            }
        };
    }

    private static Document render(Component<?, ?> component) {
        TreeBuilder treeBuilder = new TreeBuilder(
                new QualifiedSessionId("device", "session"),
                TreePositionPath.of("1"),
                new ComponentContext(),
                _ -> {});
        component.render(treeBuilder);
        return Jsoup.parseBodyFragment(treeBuilder.html());
    }
}
