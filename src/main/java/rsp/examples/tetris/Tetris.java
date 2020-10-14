package rsp.examples.tetris;

import rsp.App;
import rsp.Component;
import rsp.jetty.JettyServer;
import rsp.server.StaticResources;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static rsp.dsl.Html.*;

public class Tetris {
    public static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws Exception {
        final Component<State> render = useState ->
            html(
                head(link(attr("rel", "stylesheet"), attr("href","/res/style.css"))),
                body(
                    div(attr("class", "stage"),
                        of(Arrays.stream(useState.get().stage.cells).flatMap(row ->
                                Arrays.stream(row)).map(cell ->
                                    div(attr("class", "cell t" + (char)cell.type))))),
                        window().on("onkeydown", c -> System.out.println("key down"))));

        final var s = new JettyServer(DEFAULT_PORT,
                                "",
                                new App(State.initialState(),
                                        render),
                                Optional.of(new StaticResources(new File("src/main/java/rsp/examples/tetris"),
                                                "/res/*")));
        s.start();
        s.join();
    }

    private static class ScheduledTask {
        private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        private ScheduledFuture<?> scheduledFuture;


        public synchronized void scheduleAtFixedRate(Runnable command, int delay, int period, TimeUnit timeUnit) {
            if(scheduledFuture == null) {
                scheduledFuture = executorService.scheduleAtFixedRate(command, delay, period, timeUnit);
            }
        }

        public synchronized void cancel() {
            if(scheduledFuture != null) {
                scheduledFuture.cancel(false);
            }
        }
    }
}
