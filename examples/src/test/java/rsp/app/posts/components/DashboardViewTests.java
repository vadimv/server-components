package rsp.app.posts.components;

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
    void contract_enriches_context_with_static_dashboard_model() {
        DashboardContract contract = new DashboardContract(new TestLookup());
        ComponentContext context = contract.enrichContext(new ComponentContext());

        DashboardModel model = context.get(DashboardContract.DASHBOARD_MODEL);

        assertEquals("Dashboard", contract.title());
        assertNotNull(model);
        assertFalse(model.layout().placements().isEmpty());
    }

    @Test
    void renders_grid_with_one_graph_widget() {
        DashboardModel model = new DashboardModel(DashboardDsl.dashboard()
                .place(new TestDashboardWidget("single"), DashboardDsl.at(1, 1).span(6, 3))
                .build());

        Document document = render(new DashboardView(),
                new ComponentContext().with(DashboardContract.DASHBOARD_MODEL, model));

        assertEquals(1, document.select(".dashboard-grid").size());
        assertEquals(1, document.select(".dashboard-grid-item").size());
        assertEquals("single", document.select(".dashboard-grid-item").attr("data-widget-id"));
        assertEquals(1, document.select(".test-widget").size());
        assertTrue(document.text().contains("Dashboard"));
        assertTrue(document.text().contains("Widget single"));
    }

    private static Document render(final Component<?> component, final ComponentContext context) {
        TreeBuilder treeBuilder = new TreeBuilder(
                new QualifiedSessionId("device", "session"),
                TreePositionPath.of("1"),
                context,
                _ -> {});
        component.render(treeBuilder);
        return Jsoup.parseBodyFragment(treeBuilder.html());
    }
}
