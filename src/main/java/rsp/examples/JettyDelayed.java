package rsp.examples;


import rsp.App;
import rsp.Component;
import rsp.jetty.JettyServer;

import java.util.concurrent.*;

import static rsp.dsl.Html.*;

public class JettyDelayed {
    public static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws Exception {

        final Component<State> render = useState ->
                html(body(
                    button(attr("type", "button"),
                           text(useState.get().run ? "Stop" : "Start"),
                            event("click", c -> {
                                useState.accept(new State(useState.get().i, !useState.get().run));
                                final ScheduledTask st = new ScheduledTask();
                                st.scheduleAtFixedRate(() -> {
                                    if(useState.get().run) {
                                        useState.accept(new State(useState.get().i + 1, true));
                                    } else {
                                        st.cancel();
                                    }
                                }, 0, 500, TimeUnit.MILLISECONDS);
                            })),
                    span(text(useState.get().i))
        ));

        final var s = new JettyServer(DEFAULT_PORT,
                "",
                new App(new State(0, false),
                        render));
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

    private static class State {

        private boolean run;
        private final int i;

        public State(int i, boolean run) {
            this.i = i;
            this.run = run;
        }

    }
}
