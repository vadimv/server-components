package rsp.app.posts.components;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import rsp.app.posts.services.LogEntry;
import rsp.app.posts.services.LogStreamService;
import rsp.component.ComponentContext;
import rsp.component.TreeBuilder;
import rsp.component.definitions.Component;
import rsp.compositions.dashboard.DashboardRuntime;
import rsp.telemetry.MapTelemetryRegistry;
import rsp.telemetry.Subscription;
import rsp.telemetry.TelemetrySample;
import rsp.telemetry.TelemetrySeries;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class LogsWidgetTests {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-05-27T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void renders_static_entries_with_severity_classes() {
        Document document = render(widget(List.of(
                new LogEntry(1, CLOCK.instant(), LogEntry.Level.INFO, "Logrem ipsum.. 1"),
                new LogEntry(2, CLOCK.instant(), LogEntry.Level.WARN, "Logrem ipsum.. 2"),
                new LogEntry(3, CLOCK.instant(), LogEntry.Level.ERROR, "Logrem ipsum.. 3")
        )));

        assertEquals(1, document.select(".logs-widget").size());
        assertEquals(3, document.select(".logs-row").size());
        assertEquals(1, document.select(".logs-row-info").size());
        assertEquals(1, document.select(".logs-row-warn").size());
        assertEquals(1, document.select(".logs-row-error").size());
        assertTrue(document.text().contains("Logrem ipsum.. 1"));
        assertTrue(document.text().contains("Logrem ipsum.. 3"));
    }

    @Test
    void renders_messages_in_chronological_order() {
        Document document = render(widget(List.of(
                new LogEntry(1, CLOCK.instant(), LogEntry.Level.INFO, "Logrem ipsum.. 1"),
                new LogEntry(2, CLOCK.instant(), LogEntry.Level.INFO, "Logrem ipsum.. 2"),
                new LogEntry(3, CLOCK.instant(), LogEntry.Level.INFO, "Logrem ipsum.. 3")
        )));

        var messages = document.select(".logs-msg");
        assertEquals(3, messages.size());
        assertEquals("Logrem ipsum.. 1", messages.get(0).text());
        assertEquals("Logrem ipsum.. 2", messages.get(1).text());
        assertEquals("Logrem ipsum.. 3", messages.get(2).text());
    }

    @Test
    void renders_empty_state_when_no_entries() {
        Document document = render(widget(List.of()));

        assertTrue(document.select(".logs-row").isEmpty());
        assertTrue(document.text().contains("No log entries yet"));
    }

    @Test
    void renders_live_snapshot_from_service() {
        LogStreamService service = new LogStreamService(5, CLOCK, new Random(0L));
        service.emitNextEntry();
        service.emitNextEntry();
        LogsWidget widget = new LogsWidget(definition(),
                DemoDashboards.runtime(DemoTelemetry.registry(
                        new rsp.app.posts.services.CommentRateStreamService(List.of(1), 1, CLOCK),
                        service)));

        Document document = render(widget);

        assertEquals(2, document.select(".logs-row").size());
        assertTrue(document.text().contains("Lorem ipsum dolor sit amet, consectetur adipiscing elit. 1"));
        assertTrue(document.text().contains("Lorem ipsum dolor sit amet, consectetur adipiscing elit. 2"));
        assertTrue(document.select(".logs-status-live").text().contains("Live"));
        assertTrue(document.text().contains("2 events"));
        assertEquals("application.logs", definition().metadata().get("series"));
    }

    @Test
    void each_row_has_timestamp_level_and_message_columns() {
        Document document = render(widget(List.of(
                new LogEntry(1, CLOCK.instant(), LogEntry.Level.INFO, "Logrem ipsum.. 1")
        )));

        assertEquals(1, document.select(".logs-row > .logs-ts").size());
        assertEquals(1, document.select(".logs-row > .logs-lvl").size());
        assertEquals(1, document.select(".logs-row > .logs-msg").size());
        assertEquals("INFO", document.select(".logs-lvl").text());
    }

    @Test
    void live_widget_includes_client_connection_status_sync() {
        LogsWidget widget = widget(List.of());

        Document document = render(widget);
        String script = document.select("script").html();

        assertTrue(script.contains("data-rsp-connection"));
        assertTrue(script.contains("rsp:connection-state"));
        assertTrue(script.contains("Reconnecting"));
        assertTrue(script.contains("logs-status-lost"));
    }

    private static Document render(final Component<?, ?> component) {
        TreeBuilder treeBuilder = new TreeBuilder(
                new QualifiedSessionId("device", "session"),
                TreePositionPath.of("1"),
                new ComponentContext(),
                _ -> {});
        component.render(treeBuilder);
        return Jsoup.parseBodyFragment(treeBuilder.html());
    }

    private static LogsDefinition definition() {
        return new LogsDefinition("logs", "Logs", "Live application log stream",
                DemoTelemetry.LOG_ENTRIES);
    }

    private static LogsWidget widget(List<LogEntry> entries) {
        TelemetrySeries<LogEntry> source = new TelemetrySeries<>() {
            @Override
            public List<TelemetrySample<LogEntry>> snapshot() {
                return entries.stream()
                        .map(entry -> TelemetrySample.good(entry, entry.timestamp()))
                        .toList();
            }

            @Override
            public Subscription subscribe(Consumer<List<TelemetrySample<LogEntry>>> subscriber) {
                return Subscription.none();
            }
        };
        DashboardRuntime runtime = DemoDashboards.runtime(MapTelemetryRegistry.builder()
                .bindSeries(DemoTelemetry.LOG_ENTRIES, source)
                .build());
        return new LogsWidget(definition(), runtime);
    }
}
