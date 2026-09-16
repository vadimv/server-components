package rsp.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rsp.dom.TreePositionPath;
import rsp.dom.XmlNs;
import rsp.metrics.MetricNames;
import rsp.metrics.MetricCatalog;
import rsp.metrics.MetricDescriptor;
import rsp.metrics.MetricObject;
import rsp.metrics.MetricObjectCatalog;
import rsp.metrics.MetricObjectType;
import rsp.metrics.MetricRegistry;
import rsp.metrics.Metrics;
import rsp.metrics.RecordingMetrics;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the three counters instrumented in {@link ComponentSegment}:
 *   - {@code rsp.segment.created}
 *   - {@code rsp.segment.unmounted}
 *   - {@code rsp.segment.update.dropped_unmounted}
 */
class ComponentSegmentMetricsTests {

    private static final TreePositionPath START_DOM_PATH = TreePositionPath.of("1");
    private static final MetricDescriptor CURRENT_LENGTH = MetricDescriptor.gauge(
            "test.component.length", "1", "Current state length", "CurrentLength");
    private static final MetricObjectType COMPONENT_METRICS = new MetricObjectType(
            "test.component", "TestComponent", MetricCatalog.of(CURRENT_LENGTH));

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
        final ComponentView<String, Object> view = intents -> s -> rc -> {
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

    @Test
    void segment_owns_one_metric_object_and_closes_it_on_unmount() {
        final MetricRegistry registry = new MetricRegistry(
                MetricNames.frameworkCatalog(), MetricObjectCatalog.of(COMPONENT_METRICS));
        final ComponentContext registryContext = new ComponentContext().with(Metrics.class, registry);
        final TreeBuilder treeBuilder = new TreeBuilder(
                sessionId, START_DOM_PATH, registryContext, commandsEnqueue);
        final AtomicReference<MetricObject> mountedObject = new AtomicReference<>();
        final ComponentCallbacks<String> callbacks = new NoOpCallbacks() {
            @Override
            public void onMounted(final ComponentSegment<String> segment,
                                  final ComponentCompositeKey componentId,
                                  final String state,
                                  final CommandsEnqueue commandsEnqueue,
                                  final StateUpdater<String> stateUpdater) {
                final MetricObject object = segment.metricObject(COMPONENT_METRICS);
                assertEquals(object, segment.metricObject(COMPONENT_METRICS));
                object.setGauge(CURRENT_LENGTH.name(), state.length());
                mountedObject.set(object);
            }

            @Override
            public void onUpdated(final ComponentSegment<String> segment,
                                  final ComponentCompositeKey componentId,
                                  final String oldState,
                                  final String newState,
                                  final StateUpdater<String> stateUpdater) {
                segment.metricObject(COMPONENT_METRICS)
                       .setGauge(CURRENT_LENGTH.name(), newState.length());
            }
        };
        final ComponentSegment<String> segment = createSegment(
                treeBuilder, registryContext, callbacks, state -> state);

        treeBuilder.openComponent(segment);
        segment.render(treeBuilder);
        treeBuilder.closeComponent();

        assertEquals(1, registry.activeMetricObjectCount());
        assertEquals(0, mountedObject.get().instanceNumber());
        assertEquals("initial".length(), mountedObject.get().value(CURRENT_LENGTH.name()));

        segment.applyStateTransformation(_ -> "updated state");

        assertEquals(1, registry.activeMetricObjectCount());
        assertEquals("updated state".length(), mountedObject.get().value(CURRENT_LENGTH.name()));

        segment.unmount();

        assertTrue(mountedObject.get().isClosed());
        assertEquals(0, registry.activeMetricObjectCount());
        assertEquals(0, registry.value(MetricNames.METRIC_OBJECTS_ACTIVE));
    }

    @Test
    void failed_initial_render_closes_objects_opened_by_lifecycle_callbacks() {
        final MetricRegistry registry = new MetricRegistry(
                MetricNames.frameworkCatalog(), MetricObjectCatalog.of(COMPONENT_METRICS));
        final ComponentContext registryContext = new ComponentContext().with(Metrics.class, registry);
        final TreeBuilder treeBuilder = new TreeBuilder(
                sessionId, START_DOM_PATH, registryContext, commandsEnqueue);
        final AtomicReference<MetricObject> openedObject = new AtomicReference<>();
        final ComponentCallbacks<String> callbacks = new NoOpCallbacks() {
            @Override
            public void onBeforeRendered(final ComponentSegment<String> segment, final String state) {
                openedObject.set(segment.metricObject(COMPONENT_METRICS));
            }
        };
        final ComponentSegment<String> segment = createSegment(
                treeBuilder,
                registryContext,
                callbacks,
                _ -> {
                    throw new IllegalStateException("render failed");
                });

        treeBuilder.openComponent(segment);
        segment.render(treeBuilder);
        treeBuilder.closeComponent();

        assertTrue(openedObject.get().isClosed());
        assertEquals(0, registry.activeMetricObjectCount());
    }

    @Test
    void unmount_callback_failure_does_not_leak_segment_metric_objects() {
        final MetricRegistry registry = new MetricRegistry(
                MetricNames.frameworkCatalog(), MetricObjectCatalog.of(COMPONENT_METRICS));
        final ComponentContext registryContext = new ComponentContext().with(Metrics.class, registry);
        final TreeBuilder treeBuilder = new TreeBuilder(
                sessionId, START_DOM_PATH, registryContext, commandsEnqueue);
        final ComponentCallbacks<String> callbacks = new NoOpCallbacks() {
            @Override
            public void onMounted(final ComponentSegment<String> segment,
                                  final ComponentCompositeKey componentId,
                                  final String state,
                                  final CommandsEnqueue commandsEnqueue,
                                  final StateUpdater<String> stateUpdater) {
                segment.metricObject(COMPONENT_METRICS);
            }

            @Override
            public void onUnmounted(final ComponentCompositeKey componentId, final String state) {
                throw new IllegalStateException("application cleanup failed");
            }
        };
        final ComponentSegment<String> segment = createSegment(
                treeBuilder, registryContext, callbacks, state -> state);
        treeBuilder.openComponent(segment);
        segment.render(treeBuilder);
        treeBuilder.closeComponent();

        assertThrows(IllegalStateException.class, segment::unmount);

        assertEquals(0, registry.activeMetricObjectCount());
        assertEquals(1, registry.value(MetricNames.SEGMENT_UNMOUNTED));
    }

    private ComponentSegment<String> createSegment(
            final TreeBuilder treeBuilder,
            final ComponentContext context,
            final ComponentCallbacks<String> callbacks,
            final java.util.function.Function<String, String> renderedText) {
        final ComponentView<String, Object> view = intents -> state -> renderContext -> {
            final String text = renderedText.apply(state);
            renderContext.openNode(XmlNs.html, "div", false);
            renderContext.addTextNode(text);
            renderContext.closeNode("div", false);
        };
        return new ComponentSegment<>(
                componentId,
                (key, componentContext) -> "initial",
                (componentContext, state) -> componentContext,
                view,
                callbacks,
                treeBuilder,
                context,
                commandsEnqueue);
    }

    private static class NoOpCallbacks implements ComponentCallbacks<String> {
        @Override public boolean onBeforeUpdated(String newState, CommandsEnqueue cmd) { return true; }
        @Override public void onAfterRendered(String state, Subscriber sub, CommandsEnqueue cmd, StateUpdater<String> upd) {}
        @Override public void onMounted(ComponentCompositeKey id, String state, StateUpdater<String> upd) {}
        @Override public void onUpdated(ComponentCompositeKey id, String oldS, String newS, StateUpdater<String> upd) {}
        @Override public void onUnmounted(ComponentCompositeKey id, String state) {}
    }
}
