package rsp.compositions.dashboard;

import org.junit.jupiter.api.Test;
import rsp.component.definitions.Component;
import rsp.compositions.block.BlockMetadata;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DashboardMetadataTests {

    @Test
    void exposes_layout_and_widget_metadata_for_agents() {
        DashboardDefinition definition = DashboardDsl.dashboard("agent", "Operations")
                .columns(12)
                .rowHeightPx(96)
                .gap("1rem")
                .place(new TestWidgetDefinition("agent-visible"),
                        DashboardDsl.at(2, 3).span(4, 2))
                .build();
        DashboardBlock block = new DashboardBlock(definition, TestDashboardRuntime.customWidgets());

        BlockMetadata metadata = block.blockMetadata();

        assertEquals("Operations", metadata.title());
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

        Map<?, ?> widgetDefinition = (Map<?, ?>) widget.get("definition");
        assertEquals("agent-visible".length(), widgetDefinition.get("value"));
        assertFalse(containsComponent(metadata.state()),
                "Agent metadata should expose structured data, not server component instances.");
    }

    private static boolean containsComponent(final Object value) {
        if (value instanceof Component<?, ?>) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(DashboardMetadataTests::containsComponent);
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
}
