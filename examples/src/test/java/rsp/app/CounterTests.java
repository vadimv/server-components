package rsp.app;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.component.ComponentSegment;
import rsp.component.ComponentView;
import rsp.component.StateUpdater;
import rsp.component.TreeBuilder;
import rsp.dom.TreePositionPath;
import rsp.dom.XmlNs;
import rsp.metrics.MetricNames;
import rsp.metrics.MetricObjectTypes;
import rsp.metrics.MetricRegistry;
import rsp.metrics.Metrics;
import rsp.metrics.RecordingMetrics;
import rsp.page.QualifiedSessionId;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CounterTests {

    @Test
    void increment_metric_is_cumulative_across_page_components() {
        final RecordingMetrics metrics = new RecordingMetrics();
        final ComponentView<Integer, Counter.CounterIntent> view = _ -> _ -> _ -> { };
        final Counter.CounterComponent firstPage = new Counter.CounterComponent(view, metrics);
        final Counter.CounterComponent secondPage = new Counter.CounterComponent(view, metrics);

        final RecordingStateUpdater firstState = new RecordingStateUpdater(0);
        firstPage.onIntentDispatched(Counter.CounterIntent.INCREMENT, 0, firstState);
        firstPage.onIntentDispatched(Counter.CounterIntent.INCREMENT, 0, firstState);

        final RecordingStateUpdater secondState = new RecordingStateUpdater(0);
        secondPage.onIntentDispatched(Counter.CounterIntent.INCREMENT, 0, secondState);

        assertEquals(2, firstState.value);
        assertEquals(1, secondState.value);
        assertEquals(3, metrics.counter(Counter.INCREMENTS.name()));
    }

    @Test
    void each_mounted_counter_has_an_independent_lifecycle_metric_object() {
        final MetricRegistry metrics = new MetricRegistry(
                MetricNames.frameworkCatalog().with(Counter.INCREMENTS),
                MetricObjectTypes.frameworkCatalog().with(Counter.METRIC_OBJECT_TYPE));
        final ComponentContext context = new ComponentContext().with(Metrics.class, metrics);
        final ComponentView<Integer, Counter.CounterIntent> view = _ -> _ -> renderContext -> {
            renderContext.openNode(XmlNs.html, "div", false);
            renderContext.closeNode("div", false);
        };
        final Counter.CounterComponent first = new Counter.CounterComponent(view, metrics);
        final Counter.CounterComponent second = new Counter.CounterComponent(view, metrics);
        final ComponentSegment<Integer> firstSegment = mount(first, "first", context);
        final ComponentSegment<Integer> secondSegment = mount(second, "second", context);

        first.onIntentDispatched(Counter.CounterIntent.INCREMENT, 0, firstSegment);
        first.onIntentDispatched(Counter.CounterIntent.INCREMENT, 1, firstSegment);
        second.onIntentDispatched(Counter.CounterIntent.INCREMENT, 0, secondSegment);

        assertEquals(3, metrics.value(Counter.INCREMENTS.name()));
        assertEquals(2, metrics.metricObjectSnapshots().size());
        assertEquals(2, metrics.metricObjectSnapshots().get(0).metrics()
                .value(Counter.CURRENT_VALUE.name()));
        assertEquals(1, metrics.metricObjectSnapshots().get(1).metrics()
                .value(Counter.CURRENT_VALUE.name()));

        firstSegment.unmount();
        assertEquals(1, metrics.activeMetricObjectCount());
        assertEquals(1, metrics.metricObjectSnapshots().getFirst().metrics()
                .value(Counter.CURRENT_VALUE.name()));

        secondSegment.unmount();
        assertEquals(0, metrics.activeMetricObjectCount());
    }

    private static ComponentSegment<Integer> mount(final Counter.CounterComponent component,
                                                   final String session,
                                                   final ComponentContext context) {
        final QualifiedSessionId sessionId = new QualifiedSessionId("device", session);
        final TreePositionPath path = TreePositionPath.of("1");
        final TreeBuilder treeBuilder = new TreeBuilder(sessionId, path, context, _ -> { });
        final ComponentSegment<Integer> segment = component.createComponentSegment(
                sessionId, path, treeBuilder, context, _ -> { });
        treeBuilder.openComponent(segment);
        segment.render(treeBuilder);
        treeBuilder.closeComponent();
        return segment;
    }

    private static final class RecordingStateUpdater implements StateUpdater<Integer> {
        private int value;

        private RecordingStateUpdater(final int value) {
            this.value = value;
        }

        @Override
        public void setState(final Integer newState) {
            value = newState;
        }

        @Override
        public void applyStateTransformation(final UnaryOperator<Integer> stateTransformer) {
            value = stateTransformer.apply(value);
        }

        @Override
        public void applyStateTransformationIfPresent(
                final Function<Integer, Optional<Integer>> stateTransformer) {
            stateTransformer.apply(value).ifPresent(next -> value = next);
        }
    }
}
