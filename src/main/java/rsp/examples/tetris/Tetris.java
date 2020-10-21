package rsp.examples.tetris;

import rsp.App;
import rsp.Component;
import rsp.jetty.JettyServer;
import rsp.server.StaticResources;

import java.io.File;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static rsp.dsl.Html.*;

public class Tetris {
    public static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws Exception {
        final AtomicReference<ScheduledFuture<?>> timer = new AtomicReference();
        final Component<State> render = useState ->
            html(on("keydown",  c -> {
                        final String keyCode = c.eventObject().apply("keyCode").orElse("noKeyCode");
                        final State s = useState.get();
                        switch (keyCode) {
                            case "37": s.tryMoveLeft().ifPresent(ns -> useState.accept(ns)); break;
                            case "39": s.tryMoveRight().ifPresent(ns -> useState.accept(ns)); break;
                            case "40": s.tryMoveDown().ifPresent(ns -> useState.accept(ns)); break;
                            case "38": s.tryRotate().ifPresent(ns -> useState.accept(ns)); break;
                        }
                    }),
                head(link(attr("rel", "stylesheet"), attr("href","/res/style.css"))),
                body(
                    div(attr("class", "stage"),
                        of(Arrays.stream(useState.get().stage.cells()).flatMap(row ->
                                CharBuffer.wrap(row).chars().mapToObj(i -> (char)i)).map(cell ->
                                    div(attr("class", "cell t" + cell))))),
                    div(attr("class", "sidebar"),
                        span(text("Score: " + useState.get().score())),
                        button(attr("type", "button"),
                               when(useState.get().isRunning, attr("disabled", "true")),
                               text("Start"),
                               on("click", c -> {
                                       State.initialState().start().newTetramino().ifPresent(ns -> useState.accept(ns));
                                       timer.set(c.scheduleAtFixedRate(() -> {
                                           final State s = useState.get();
                                           s.tryMoveDown().ifPresentOrElse(ns -> {
                                              useState.accept(ns);
                                           }, () -> {
                                               s.newTetramino().ifPresentOrElse(ns -> useState.accept(ns), () -> {
                                                   timer.get().cancel(false);
                                                   useState.accept(s.stop());
                                               });
                                           });
                                       }, 0, 1, TimeUnit.SECONDS));
                               })))
                ));

        final var s = new JettyServer(DEFAULT_PORT,
                                "",
                                new App(State.initialState(),
                                        render),
                                Optional.of(new StaticResources(new File("src/main/java/rsp/examples/tetris"),
                                                "/res/*")));
        s.start();
        s.join();
    }
}
