package rsp.app.posts.components;

import org.junit.jupiter.api.Test;
import rsp.compositions.block.BlockMetadata;
import rsp.compositions.dashboard.DashboardBlock;
import rsp.telemetry.MapTelemetryRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DashboardBlockMetadataTests {

    @Test
    void exposes_immutable_widget_definitions_for_agents() {
        DashboardBlock block = new DashboardBlock(
                DemoDashboards.definition(),
                DemoDashboards.runtime(MapTelemetryRegistry.builder().build()));

        BlockMetadata metadata = block.blockMetadata();

        java.util.List<?> widgets = (java.util.List<?>) metadata.state().get("widgets");
        Map<?, ?> widget = (Map<?, ?>) widgets.getFirst();
        Map<?, ?> definition = (Map<?, ?>) widget.get("definition");

        assertEquals("comments-rate", widget.get("id"));
        assertEquals("trend", widget.get("kind"));
        assertEquals("comments.rate", definition.get("series"));
        assertEquals("comments/sec", definition.get("unit"));
        assertEquals(30_000L, definition.get("windowMs"));
    }
}
