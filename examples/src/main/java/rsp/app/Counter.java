package rsp.app;

import rsp.component.ComponentView;
import rsp.component.definitions.LocalStateComponent;
import rsp.http.WebServer;
import rsp.metrics.runtime.MetricsRuntime;

import static rsp.dsl.Html.*;

public final class Counter {
    private enum CounterIntent {
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
        try (MetricsRuntime metricsRuntime = MetricsRuntime.withPlatformJmx()) {
            final var server = new WebServer(8080, _ ->
                    new LocalStateComponent<>((_, _) -> 0, view, (state, intent) -> state + 1),
                                             metricsRuntime.metrics());
            Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(server::stop));
            System.out.println("http://localhost:8080");
            System.out.println("JMX: rsp.metrics / Framework (local attach; no JMX port opened)");
            server.start();
            server.join();
        }
    }
}
