package rsp.examples.tetris;

import rsp.App;
import rsp.Component;
import rsp.examples.crud.entities.Principal;
import rsp.jetty.JettyServer;
import rsp.server.StaticResources;

import java.io.File;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static rsp.dsl.Html.*;

public class Tetris {
    public static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws Exception {
        final Map<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
        final Component<State> component = useState ->
            html(on("keydown", false, c -> {
                        final String keyCode = c.eventObject().value("keyCode").map(Object::toString).orElse("noKeyCode");
                        final State s = useState.get();
                        switch (keyCode) {
                            case "37": s.tryMoveLeft().ifPresent(ns -> useState.accept(ns)); break;
                            case "39": s.tryMoveRight().ifPresent(ns -> useState.accept(ns)); break;
                            case "40": s.tryMoveDown().ifPresent(ns -> useState.accept(ns)); break;
                            case "38": s.tryRotate().ifPresent(ns -> useState.accept(ns)); break;
                        }
                    }),
                head(link(attr("rel", "stylesheet"), attr("href","/res/style.css"))),
                body(div(attr("class", "tetris-wrapper"),
                    div(attr("class", "stage"),
                        of(Arrays.stream(useState.get().stage.cells()).flatMap(row ->
                                CharBuffer.wrap(row).chars().mapToObj(i -> (char)i)).map(cell ->
                                    div(attr("class", "cell t" + cell))))),
                    div(attr("class", "sidebar"),
                        div(attr("id", "score"), text("Score: " + useState.get().score())),
                        div(attr("id", "start"),
                            button(attr("id", "start-btn"), attr("type", "button"),
                               when(useState.get().isRunning, () -> attr("disabled")),
                               text("Start"),
                               on("click", c -> {
                                   State.initialState().start().newTetramino().ifPresent(ns -> useState.accept(ns));
                                   timers.put(c.sessionId().sessionId,
                                              c.scheduleAtFixedRate(() -> useState.acceptOptional(
                                                      s -> s.tryMoveDown()
                                                            .or(() -> s.newTetramino())
                                                            .or(() -> {
                                                               timers.get(c.sessionId().sessionId).cancel(false);
                                                               return Optional.of(s.stop());
                                   })), 0, 1, TimeUnit.SECONDS));
                               })))))));
        final var s = new JettyServer(DEFAULT_PORT,
                                     "",
                                      new App(State.initialState(),
                                              component),
                                      new StaticResources(new File("src/main/java/rsp/examples/tetris"),
                                                                   "/res/*"));
        s.start();
        s.join();
    }
}
