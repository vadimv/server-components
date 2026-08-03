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
    void contract_initializes_its_local_dashboard_state() {
        DashboardModel model = new DashboardModel(DashboardDsl.dashboard()
                .place(new TestDashboardWidget("only"), DashboardDsl.at(1, 1).span(6, 3))
                .build());
        DashboardContract contract = new DashboardContract(model);
        DashboardView.DashboardState state = contract.initStateSupplier().getState(null, new ComponentContext());

        assertEquals("Dashboard", contract.title());
        assertSame(model, state.model());
        assertFalse(state.model().layout().placements().isEmpty());
    }

    @Test
    void renders_grid_with_one_graph_widget() {
        DashboardModel model = new DashboardModel(DashboardDsl.dashboard()
                .place(new TestDashboardWidget("single"), DashboardDsl.at(1, 1).span(6, 3))
                .build());

        Document document = render(new DashboardContract(model), new ComponentContext());

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
