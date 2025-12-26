package rsp.app.gameoflife;

import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.StateUpdate;
import rsp.component.definitions.Component;
import rsp.jetty.WebServer;
import rsp.server.StaticResources;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static rsp.dsl.Html.*;


/**
 * An implementation of Conway's Game of Life.
 */
public class Life {
    private static final int NEXT_GENERATION_DELAY_MS = 50;

    public static void main(String[] args) {
        final Component<State> componentDefinition = new Component<>() {

            private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(8);
            private final Map<Object, ScheduledFuture<?>> schedules = new HashMap<>();


            @Override
            public ComponentStateSupplier<State> initStateSupplier() {
                return (key, httpStateOrigin) -> State.initialState();
            }

            @Override
            public ComponentView<State> componentView() {
                return  newState -> state -> {
                    final var cells = state.board.cells;
                    return html(head(title("Conway's Game of Life"),
                                    link(attr("rel", "stylesheet"),
                                            attr("href", "/res/style.css"))),
                            body(div(attr("class", "tetris-wrapper"),
                                            div(attr("class", "board"),
                                                    of(IntStream.range(0, cells.length)
                                                            .mapToObj(index ->
                                                                    div(attr("class", "c" + (cells[index] ? "1" : "0")),
                                                                            when(!state.isRunning,
                                                                                    on("click", c -> {
                                                                                        System.out.println("Clicked x=" + Board.x(index) + " y=" + Board.y(index));
                                                                                        newState.setState(state.toggleCell(Board.x(index), Board.y(index)));
                                                                                    }))))))),
                                    div(attr("class", "controls"),
                                            button(attr("type", "button"),
                                                    when(state.isRunning, () -> attr("disabled")),
                                                    text("Start"),
                                                    on("click", c -> {
                                                        System.out.println("Start");
                                                        newState.applyStateTransformation(s -> s.setIsRunning(true));
                                                    })),
                                            button(attr("type", "button"),
                                                    when(!state.isRunning, () -> attr("disabled")),
                                                    text("Stop"),
                                                    on("click", c -> {
                                                        System.out.println("Stop");
                                                        newState.applyStateTransformation(s -> s.setIsRunning(false));
                                                    })),
                                            button(attr("type", "button"),
                                                    when(state.isRunning, () -> attr("disabled")),
                                                    text("Clear"),
                                                    on("click", c -> {
                                                        System.out.println("Clear");
                                                        newState.applyStateTransformation(s -> State.initialState());
                                                    })),
                                            button(attr("type", "button"),
                                                    when(state.isRunning, () -> attr("disabled")),
                                                    text("Random"),
                                                    on("click", c -> {
                                                        System.out.println("Random");
                                                        newState.applyStateTransformation(s -> State.initialState(true));
                                                    })))));
                };
            }


            @Override
            public void onComponentUpdated(ComponentCompositeKey componentId, State oldState, State newState, StateUpdate<State> stateUpdate) {
                if (!oldState.isRunning && newState.isRunning) {
                    scheduleAtFixedRate(() -> stateUpdate.applyStateTransformation(State::advance),
                            componentId,
                            0,
                            NEXT_GENERATION_DELAY_MS,
                            TimeUnit.MILLISECONDS);
                } else if (oldState.isRunning && !newState.isRunning) {
                    cancelSchedule(componentId);
                }
            }

            private void scheduleAtFixedRate(final Runnable command, final Object key, final long initialDelay, final long period, final TimeUnit unit) {
                final ScheduledFuture<?> timer = scheduledExecutorService.scheduleAtFixedRate(command, initialDelay, period, unit);
                schedules.put(key, timer);
            }


            @Override
            public void onComponentUnmounted(ComponentCompositeKey componentId, State state) {
                cancelSchedule(componentId);
            }

            private void cancelSchedule(final Object key) {
                final ScheduledFuture<?> schedule = schedules.get(key);
                if (schedule != null) {
                    schedule.cancel(true);
                    schedules.remove(key);
                }
            }
        };

        final var s = new WebServer(8082,
                                      httpRequest -> componentDefinition,
                                      new StaticResources(new File("src/test/java/rsp/examples/gameoflife"),
                                                         "/res/"));
        s.start();
        s.join();
    }
}
