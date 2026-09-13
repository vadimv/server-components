package rsp.app;

import rsp.component.ComponentView;
import rsp.component.ComponentStateSupplier;
import rsp.component.StateUpdater;
import rsp.component.definitions.Component;
import rsp.http.WebServer;
import rsp.metrics.MetricCatalog;
import rsp.metrics.MetricDescriptor;
import rsp.metrics.MetricNames;
import rsp.metrics.Metrics;
import rsp.metrics.runtime.MetricsRuntime;

import static rsp.dsl.Html.*;

public final class Counter {
    static final MetricDescriptor INCREMENTS = MetricDescriptor.counter(
            "app.counter.increments",
            "1",
            "Counter increments handled across all page sessions",
            "AppCounterIncrements");

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
        try (MetricsRuntime metricsRuntime = MetricsRuntime.withPlatformJmx(catalog)) {
            final var server = new WebServer(
                    8080,
                    _ -> new CounterComponent(view, metricsRuntime.metrics()),
                    metricsRuntime.metrics());
            Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(server::stop));
            System.out.println("http://localhost:8080");
            System.out.println("JMX: rsp.metrics / Framework (local attach; no JMX port opened)");
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
    }
}
