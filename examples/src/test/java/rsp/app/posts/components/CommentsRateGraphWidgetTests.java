package rsp.app.posts.components;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import rsp.app.posts.components.DashboardModel.GraphSample;
import rsp.component.ComponentContext;
import rsp.component.TreeBuilder;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommentsRateGraphWidgetTests {

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
        assertFalse(document.select(".comments-rate-line[points]").isEmpty());
    }

    @Test
    void renders_flat_data_without_dividing_by_zero() {
        Document document = render(new CommentsRateGraphWidget(List.of(
                new GraphSample("10:00", 7),
                new GraphSample("11:00", 7),
                new GraphSample("12:00", 7)
        )));

        assertEquals("18.0,70.0 160.0,70.0 302.0,70.0",
                document.select(".comments-rate-line").attr("points"));
        assertEquals("7", document.select(".dashboard-widget-value").text());
    }

    @Test
    void renders_empty_data_without_failing() {
        Document document = render(new CommentsRateGraphWidget(List.of()));

        assertEquals("18.0,70.0 302.0,70.0",
                document.select(".comments-rate-line").attr("points"));
        assertTrue(document.text().contains("No data yet"));
        assertEquals("0", document.select(".dashboard-widget-value").text());
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
