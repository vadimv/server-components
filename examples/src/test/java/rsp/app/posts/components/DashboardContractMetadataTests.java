package rsp.app.posts.components;

import org.junit.jupiter.api.Test;
import rsp.app.posts.services.CommentRateStreamService;
import rsp.app.posts.services.LogStreamService;
import rsp.component.definitions.Component;
import rsp.compositions.contract.ContractMetadata;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DashboardContractMetadataTests {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-05-25T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void exposes_layout_and_widget_metadata_for_agents() {
        DashboardLayout layout = DashboardDsl.dashboard()
                .columns(12)
                .rowHeightPx(96)
                .gap("1rem")
                .place(new TestDashboardWidget("agent-visible"),
                        DashboardDsl.at(2, 3).span(4, 2))
                .build();
        DashboardContract contract = new DashboardContract(new TestLookup(), new DashboardModel(layout));

        ContractMetadata metadata = contract.contractMetadata();

        assertEquals("Dashboard", metadata.title());
        assertEquals(12, metadata.state().get("columns"));
        assertEquals(96, metadata.state().get("rowHeightPx"));
        assertEquals("1rem", metadata.state().get("gap"));

        List<?> widgets = (List<?>) metadata.state().get("widgets");
        assertEquals(1, widgets.size());

        Map<?, ?> widget = (Map<?, ?>) widgets.getFirst();
        assertEquals("agent-visible", widget.get("id"));
        assertEquals("Widget agent-visible", widget.get("title"));
        assertEquals("test-widget", widget.get("kind"));
        assertEquals("Test widget agent-visible", widget.get("description"));

        Map<?, ?> grid = (Map<?, ?>) widget.get("grid");
        assertEquals(2, grid.get("column"));
        assertEquals(3, grid.get("row"));
        assertEquals(4, grid.get("columnSpan"));
        assertEquals(2, grid.get("rowSpan"));

        Map<?, ?> state = (Map<?, ?>) widget.get("state");
        assertEquals("agent-visible".length(), state.get("value"));
        assertFalse(containsComponent(metadata.state()),
                "Agent metadata should expose structured data, not server component instances.");
    }

    private static boolean containsComponent(final Object value) {
        if (value instanceof Component<?>) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(DashboardContractMetadataTests::containsComponent);
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (containsComponent(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void exposes_live_widget_state_for_agents() {
        CommentRateStreamService service = new CommentRateStreamService(List.of(100, 120), 5, CLOCK);
        service.emitNextSample();
        service.emitNextSample();
        LogStreamService logService = new LogStreamService(5, CLOCK, new Random(0L));
        DashboardContract contract = new DashboardContract(new TestLookup(),
                DashboardModel.live(service, logService));

        ContractMetadata metadata = contract.contractMetadata();

        List<?> widgets = (List<?>) metadata.state().get("widgets");
        Map<?, ?> widget = (Map<?, ?>) widgets.getFirst();
        Map<?, ?> state = (Map<?, ?>) widget.get("state");

        assertEquals("comments-rate", widget.get("id"));
        assertEquals("line-chart", widget.get("kind"));
        assertEquals(true, state.get("live"));
        assertEquals("comments/sec", state.get("unit"));
        assertEquals("Last 30 seconds", state.get("window"));
        assertEquals(120, state.get("currentValue"));
        assertEquals(2, state.get("sampleCount"));
    }
}
