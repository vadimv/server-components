package rsp.app.posts.components;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import rsp.app.posts.services.CommentRateStreamService;
import rsp.component.ComponentContext;
import rsp.component.TreeBuilder;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommentsRateGraphWidgetTests {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-05-25T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void renders_title_svg_and_current_value() {
        Document document = render(new CommentsRateGraphWidget(List.of(
                new GraphSample("10:00", 8),
                new GraphSample("11:00", 12),
                new GraphSample("12:00", 10)
        )));

        assertEquals(1, document.select(".comments-rate-widget").size());
        assertEquals(1, document.select(".comments-rate-chart").size());
        assertTrue(document.text().contains("Comments rate"));
        assertEquals("10", document.select(".dashboard-widget-value").text());
        assertTrue(document.text().contains("comments/hour"));
        assertEquals("Comments/hour", document.select(".comments-rate-legend-label").text());
        assertEquals(3, document.select(".comments-rate-grid-line").size());
        assertEquals(3, document.select(".comments-rate-tick-label").size());
        assertEquals(3, document.select(".comments-rate-x-label").size());
        String linePath = document.select(".comments-rate-line").attr("d");
        assertTrue(linePath.startsWith("M"), "line path should start with a move command: " + linePath);
        assertTrue(linePath.contains(" L"), "line path should contain straight-line segments: " + linePath);
        assertTrue(document.select(".comments-rate-area").isEmpty());
    }

    @Test
    void renders_flat_data_without_dividing_by_zero() {
        Document document = render(new CommentsRateGraphWidget(List.of(
                new GraphSample("10:00", 7),
                new GraphSample("11:00", 7),
                new GraphSample("12:00", 7)
        )));

        String linePath = document.select(".comments-rate-line").attr("d");
        assertEquals("M42.0,81.0 L193.0,81.0 L344.0,81.0", linePath);
        assertEquals("7", document.select(".dashboard-widget-value").text());
        assertTrue(document.select(".comments-rate-tick-label").text().contains("7"));
        assertFalse(document.html().contains("NaN"));
    }

    @Test
    void renders_empty_data_without_failing() {
        Document document = render(new CommentsRateGraphWidget(List.of()));

        String linePath = document.select(".comments-rate-line").attr("d");
        assertEquals("M42.0,81.0 L344.0,81.0", linePath);
        assertTrue(document.select(".comments-rate-area").isEmpty());
        assertEquals("Comments/hour", document.select(".comments-rate-legend-label").text());
        assertEquals(3, document.select(".comments-rate-grid-line").size());
        assertTrue(document.text().contains("No data yet"));
        assertEquals("0", document.select(".dashboard-widget-value").text());
        assertFalse(document.html().contains("NaN"));
    }

    @Test
    void renders_single_sample_as_a_visible_short_segment() {
        Document document = render(new CommentsRateGraphWidget(List.of(
                new GraphSample("10:00", 7)
        )));

        String linePath = document.select(".comments-rate-line").attr("d");
        assertEquals("M185.0,81.0 L201.0,81.0", linePath);
        assertEquals("7", document.select(".dashboard-widget-value").text());
        assertFalse(document.html().contains("NaN"));
    }

    @Test
    void renders_live_comments_per_second_snapshot() {
        CommentRateStreamService service = new CommentRateStreamService(List.of(100, 120), 5, CLOCK);
        service.emitNextSample();
        service.emitNextSample();
        CommentsRateGraphWidget widget = CommentsRateGraphWidget.live(service);

        Document document = render(widget);
        Map<String, Object> metadata = widget.metadataState();

        assertEquals("120", document.select(".dashboard-widget-value").text());
        assertTrue(document.text().contains("comments/sec"));
        assertEquals("Comments/sec", document.select(".comments-rate-legend-label").text());
        assertEquals(true, metadata.get("live"));
        assertEquals("comments/sec", metadata.get("unit"));
        assertEquals(2, metadata.get("sampleCount"));
        assertEquals(120, metadata.get("currentValue"));
    }

    private static Document render(final Component<?> component) {
        TreeBuilder treeBuilder = new TreeBuilder(
                new QualifiedSessionId("device", "session"),
                TreePositionPath.of("1"),
                new ComponentContext(),
                _ -> {});
        component.render(treeBuilder);
        return Jsoup.parseBodyFragment(treeBuilder.html());
    }
}
