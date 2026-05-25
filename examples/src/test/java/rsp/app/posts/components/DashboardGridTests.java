package rsp.app.posts.components;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.component.TreeBuilder;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

import static org.junit.jupiter.api.Assertions.*;

class DashboardGridTests {

    @Test
    void renders_grid_items_with_declared_placement_styles() {
        DashboardLayout layout = DashboardDsl.dashboard()
                .columns(12)
                .rowHeightPx(96)
                .gap("1rem")
                .place(new TestDashboardWidget("alpha"), DashboardDsl.at(1, 1).span(6, 3))
                .place(new TestDashboardWidget("beta"), DashboardDsl.at(7, 1).span(6, 2))
                .build();

        Document document = render(new DashboardGrid(layout));

        Element grid = document.selectFirst(".dashboard-grid");
        assertNotNull(grid);
        assertEquals("--dashboard-columns: 12; --dashboard-row-height: 96px; --dashboard-gap: 1rem",
                grid.attr("style"));

        assertEquals(2, document.select(".dashboard-grid-item").size());
        assertEquals("grid-column: 1 / span 6; grid-row: 1 / span 3",
                document.select(".dashboard-grid-item[data-widget-id=alpha]").attr("style"));
        assertEquals("grid-column: 7 / span 6; grid-row: 1 / span 2",
                document.select(".dashboard-grid-item[data-widget-id=beta]").attr("style"));
        assertTrue(document.text().contains("Widget alpha"));
        assertTrue(document.text().contains("Widget beta"));
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
