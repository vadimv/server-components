package rsp.compositions.dashboard;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.component.TreeBuilder;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

import static org.junit.jupiter.api.Assertions.*;

class DashboardViewTests {

    @Test
    void block_initializes_its_local_dashboard_state() {
        DashboardDefinition definition = DashboardDsl.dashboard("dashboard", "Dashboard")
                .place(new TestWidgetDefinition("only"), DashboardDsl.at(1, 1).span(6, 3))
                .build();
        DashboardBlock block = new DashboardBlock(definition, TestDashboardRuntime.customWidgets());
        DashboardView.DashboardState state = block.initStateSupplier().getState(null, new ComponentContext());

        assertEquals("Dashboard", block.title());
        assertSame(definition, state.definition());
        assertFalse(state.definition().widgets().isEmpty());
    }

    @Test
    void renders_grid_with_one_graph_widget() {
        DashboardDefinition definition = DashboardDsl.dashboard("dashboard", "Dashboard")
                .place(new TestWidgetDefinition("single"), DashboardDsl.at(1, 1).span(6, 3))
                .build();

        Document document = render(
                new DashboardBlock(definition, TestDashboardRuntime.customWidgets()),
                new ComponentContext());

        assertEquals(1, document.select(".dashboard-grid").size());
        assertEquals(1, document.select(".dashboard-grid-item").size());
        assertEquals("single", document.select(".dashboard-grid-item").attr("data-widget-id"));
        assertEquals(1, document.select(".test-widget").size());
        assertTrue(document.text().contains("Dashboard"));
        assertTrue(document.text().contains("Widget single"));
    }

    private static Document render(final Component<?, ?> component, final ComponentContext context) {
        TreeBuilder treeBuilder = new TreeBuilder(
                new QualifiedSessionId("device", "session"),
                TreePositionPath.of("1"),
                context,
                _ -> {});
        component.render(treeBuilder);
        return Jsoup.parseBodyFragment(treeBuilder.html());
    }
}
