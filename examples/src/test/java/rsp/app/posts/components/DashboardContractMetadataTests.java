package rsp.app.posts.components;

import org.junit.jupiter.api.Test;
import rsp.app.posts.services.CommentRateStreamService;
import rsp.app.posts.services.LogStreamService;
import rsp.compositions.contract.ContractMetadata;
import rsp.compositions.dashboard.DashboardContract;

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
    void exposes_live_widget_state_for_agents() {
        CommentRateStreamService service = new CommentRateStreamService(List.of(100, 120), 5, CLOCK);
        service.emitNextSample();
        service.emitNextSample();
        LogStreamService logService = new LogStreamService(5, CLOCK, new Random(0L));
        DashboardContract contract = new DashboardContract(new TestLookup(),
                DemoDashboards.live(service, logService));

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
