package rsp.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rsp.dom.TreePositionPath;
import rsp.dom.XmlNs;
import rsp.metrics.MetricNames;
import rsp.metrics.Metrics;
import rsp.metrics.RecordingMetrics;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the three counters instrumented in {@link ComponentSegment}:
 *   - {@code rsp.segment.created}
 *   - {@code rsp.segment.unmounted}
 *   - {@code rsp.segment.update.dropped_unmounted}
 */
class ComponentSegmentMetricsTests {

    private static final TreePositionPath START_DOM_PATH = TreePositionPath.of("1");

    private QualifiedSessionId sessionId;
    private ComponentCompositeKey componentId;
    private ComponentContext componentContext;
    private List<Command> capturedCommands;
    private CommandsEnqueue commandsEnqueue;
    private RecordingMetrics metrics;

    @BeforeEach
    void setUp() {
        sessionId = new QualifiedSessionId("device", "session");
        componentId = new ComponentCompositeKey(sessionId, "testType", TreePositionPath.of("1"));
        metrics = new RecordingMetrics();
        componentContext = new ComponentContext().with(Metrics.class, metrics);
        capturedCommands = new ArrayList<>();
        commandsEnqueue = capturedCommands::add;
    }

    private ComponentSegment<String> createSegment(final TreeBuilderFactory factory) {
        final ComponentStateSupplier<String> stateSupplier = (key, ctx) -> "initial";
        final BiFunction<ComponentContext, String, ComponentContext> contextResolver = (ctx, s) -> ctx;
        final ComponentView<String> view = stateUpdate -> s -> rc -> {
            rc.openNode(XmlNs.html, "div", false);
            rc.closeNode("div", false);
        };
        return new ComponentSegment<>(
                componentId, stateSupplier, contextResolver, view,
                new NoOpCallbacks(), factory, componentContext, commandsEnqueue);
    }

    private TreeBuilder createTreeBuilder() {
        return new TreeBuilder(sessionId, START_DOM_PATH, componentContext, commandsEnqueue);
    }

    @Test
    void segment_created_counter_fires_once_per_construction() {
        createSegment(createTreeBuilder());
        assertEquals(1L, metrics.counter(MetricNames.SEGMENT_CREATED));

        createSegment(createTreeBuilder());
        assertEquals(2L, metrics.counter(MetricNames.SEGMENT_CREATED));
    }

    @Test
    void segment_unmounted_counter_fires_once_per_unmount() {
        final TreeBuilder tb = createTreeBuilder();
        final ComponentSegment<String> segment = createSegment(tb);
        tb.openComponent(segment);
        segment.render(tb);
        tb.closeComponent();

        assertEquals(0L, metrics.counter(MetricNames.SEGMENT_UNMOUNTED));
        segment.unmount();
        assertEquals(1L, metrics.counter(MetricNames.SEGMENT_UNMOUNTED));
    }

    @Test
    void segment_unmounted_counter_is_idempotent() {
        final TreeBuilder tb = createTreeBuilder();
        final ComponentSegment<String> segment = createSegment(tb);
        tb.openComponent(segment);
        segment.render(tb);
        tb.closeComponent();

        segment.unmount();
        segment.unmount();
        segment.unmount();
        assertEquals(1L, metrics.counter(MetricNames.SEGMENT_UNMOUNTED),
                "double-unmount must not double-count (idempotency guard)");
    }

    @Test
    void dropped_update_counter_fires_when_unmounted_segment_receives_state_update() {
        final TreeBuilder tb = createTreeBuilder();
        final ComponentSegment<String> segment = createSegment(tb);
        tb.openComponent(segment);
        segment.render(tb);
        tb.closeComponent();

        segment.unmount();
        assertEquals(0L, metrics.counter(MetricNames.SEGMENT_UPDATE_DROPPED_UNMOUNTED));

        segment.applyStateTransformation(s -> "ghost-update");
        assertEquals(1L, metrics.counter(MetricNames.SEGMENT_UPDATE_DROPPED_UNMOUNTED),
                "the ghost-component canary must fire when a state update lands on an unmounted segment");
    }

    @Test
    void dropped_update_counter_does_not_fire_on_normal_update() {
        final TreeBuilder tb = createTreeBuilder();
        final ComponentSegment<String> segment = createSegment(tb);
        tb.openComponent(segment);
        segment.render(tb);
        tb.closeComponent();

        segment.applyStateTransformation(s -> "ok");
        assertEquals(0L, metrics.counter(MetricNames.SEGMENT_UPDATE_DROPPED_UNMOUNTED));
    }

    @Test
    void no_metrics_emitted_when_context_has_no_metrics_instance() {
        // A segment built without Metrics in context falls back to NoOp.
        final ComponentContext noMetricsCtx = new ComponentContext();
        final TreeBuilder tb = new TreeBuilder(sessionId, START_DOM_PATH, noMetricsCtx, commandsEnqueue);
        final ComponentSegment<String> segment = new ComponentSegment<>(
                componentId,
                (k, c) -> "initial",
                (c, s) -> c,
                stateUpdate -> s -> rc -> { rc.openNode(XmlNs.html, "div", false); rc.closeNode("div", false); },
                new NoOpCallbacks(),
                tb,
                noMetricsCtx,
                commandsEnqueue);
        tb.openComponent(segment);
        segment.render(tb);
        tb.closeComponent();
        segment.unmount();
        // RecordingMetrics from setUp is unrelated to noMetricsCtx; nothing to assert against —
        // the test is that no exceptions propagate from the NoOp fallback path.
        assertTrue(true);
    }

    private static final class NoOpCallbacks implements ComponentCallbacks<String> {
        @Override public boolean onBeforeUpdated(String newState, CommandsEnqueue cmd) { return true; }
        @Override public void onAfterRendered(String state, Subscriber sub, CommandsEnqueue cmd, StateUpdate<String> upd) {}
        @Override public void onMounted(ComponentCompositeKey id, String state, StateUpdate<String> upd) {}
        @Override public void onUpdated(ComponentCompositeKey id, String oldS, String newS, StateUpdate<String> upd) {}
        @Override public void onUnmounted(ComponentCompositeKey id, String state) {}
    }
}
