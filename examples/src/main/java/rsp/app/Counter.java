package rsp.app;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentSegment;
import rsp.component.ComponentView;
import rsp.component.ComponentStateSupplier;
import rsp.component.StateUpdater;
import rsp.component.definitions.Component;
import rsp.http.WebServer;
import rsp.metrics.MetricCatalog;
import rsp.metrics.MetricDescriptor;
import rsp.metrics.MetricNames;
import rsp.metrics.MetricObjectType;
import rsp.metrics.MetricObjectTypes;
import rsp.metrics.Metrics;
import rsp.metrics.runtime.MetricsRuntime;

import static rsp.dsl.Html.*;

public final class Counter {
    static final MetricDescriptor INCREMENTS = MetricDescriptor.counter(
            "app.counter.increments",
            "1",
            "Counter increments handled across all page sessions",
            "AppCounterIncrements");
    static final MetricDescriptor CURRENT_VALUE = MetricDescriptor.gauge(
            "app.counter.value",
            "1",
            "Current value of this counter component",
            "CurrentValue");
    static final MetricObjectType METRIC_OBJECT_TYPE = new MetricObjectType(
            "app.counter",
            "Counter",
            MetricCatalog.of(CURRENT_VALUE));

    enum CounterIntent {
        INCREMENT
    }

    static void main(final String[] args) {

        final ComponentView<Integer, CounterIntent> view = intents -> state ->
                html(
                    body(
                            h1("Current count: " + state),
                            button(on("click", _ -> intents.dispatch(CounterIntent.INCREMENT)),
                            text("Increment"))
                    )
                );
        final MetricCatalog catalog = MetricNames.frameworkCatalog().with(INCREMENTS);
        try (MetricsRuntime metricsRuntime = MetricsRuntime.withPlatformJmx(
                catalog,
                MetricObjectTypes.frameworkCatalog().with(METRIC_OBJECT_TYPE))) {
            final var server = new WebServer(8080)
                    .page("/", (_, _) -> new CounterComponent(view, metricsRuntime.metrics()))
                    .metrics(metricsRuntime.metrics());
            Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(server::stop));
            System.out.println("http://localhost:8080");
            System.out.println("JMX: rsp.metrics / Framework (local attach; no JMX port opened)");
            System.out.println("Each live page counter is under rsp.metrics / Counter");
            server.start();
            server.join();
        }
    }

    /** One instance is created per page session; all instances share the process metric sink. */
    static final class CounterComponent extends Component<Integer, CounterIntent> {
        private final ComponentView<Integer, CounterIntent> view;
        private final Metrics metrics;

        CounterComponent(final ComponentView<Integer, CounterIntent> view, final Metrics metrics) {
            this.view = view;
            this.metrics = metrics;
        }

        @Override
        public ComponentStateSupplier<Integer> initStateSupplier() {
            return (_, _) -> 0;
        }

        @Override
        public ComponentView<Integer, CounterIntent> componentView() {
            return view;
        }

        @Override
        protected void onIntent(final CounterIntent intent,
                                final Integer state,
                                final StateUpdater<Integer> stateUpdater) {
            if (intent == CounterIntent.INCREMENT) {
                metrics.incrementCounter(INCREMENTS.name());
                stateUpdater.applyStateTransformation(value -> value + 1);
            }
        }

        @Override
        public void onMounted(final ComponentSegment<Integer> segment,
                              final ComponentCompositeKey componentId,
                              final Integer state,
                              final CommandsEnqueue commandsEnqueue,
                              final StateUpdater<Integer> stateUpdater) {
            segment.metricObject(METRIC_OBJECT_TYPE).setGauge(CURRENT_VALUE.name(), state);
        }

        @Override
        public void onUpdated(final ComponentSegment<Integer> segment,
                              final ComponentCompositeKey componentId,
                              final Integer oldState,
                              final Integer newState,
                              final StateUpdater<Integer> stateUpdater) {
            segment.metricObject(METRIC_OBJECT_TYPE).setGauge(CURRENT_VALUE.name(), newState);
        }
    }
}
