package rsp.app.gameoflife;

import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.StateUpdater;
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

    private sealed interface LifeIntent permits ToggleCell, SetRunning, ResetBoard {
    }

    private record ToggleCell(int x, int y) implements LifeIntent {
    }

    private record SetRunning(boolean running) implements LifeIntent {
    }

    private record ResetBoard(boolean random) implements LifeIntent {
    }

    public static void main(String[] args) {
        final Component<State, LifeIntent> componentDefinition = new Component<>() {

            private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(8);
            private final Map<Object, ScheduledFuture<?>> schedules = new HashMap<>();


            @Override
            public ComponentStateSupplier<State> initStateSupplier() {
                return (key, httpStateOrigin) -> State.initialState();
            }

            @Override
            public ComponentView<State, LifeIntent> componentView() {
                return intents -> state -> {
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
                                                                                        intents.dispatch(new ToggleCell(Board.x(index), Board.y(index)));
                                                                                    }))))))),
                                    div(attr("class", "controls"),
                                            button(attr("type", "button"),
                                                    when(state.isRunning, () -> attr("disabled")),
                                                    text("Start"),
                                                    on("click", c -> {
                                                        System.out.println("Start");
                                                        intents.dispatch(new SetRunning(true));
                                                    })),
                                            button(attr("type", "button"),
                                                    when(!state.isRunning, () -> attr("disabled")),
                                                    text("Stop"),
                                                    on("click", c -> {
                                                        System.out.println("Stop");
                                                        intents.dispatch(new SetRunning(false));
                                                    })),
                                            button(attr("type", "button"),
                                                    when(state.isRunning, () -> attr("disabled")),
                                                    text("Clear"),
                                                    on("click", c -> {
                                                        System.out.println("Clear");
                                                        intents.dispatch(new ResetBoard(false));
                                                    })),
                                            button(attr("type", "button"),
                                                    when(state.isRunning, () -> attr("disabled")),
                                                    text("Random"),
                                                    on("click", c -> {
                                                        System.out.println("Random");
                                                        intents.dispatch(new ResetBoard(true));
                                                    })))));
                };
            }

            @Override
            protected void onIntent(final LifeIntent intent,
                                    final State state,
                                    final StateUpdater<State> stateUpdater) {
                if (intent instanceof ToggleCell toggleCell) {
                    stateUpdater.setState(state.toggleCell(toggleCell.x(), toggleCell.y()));
                } else if (intent instanceof SetRunning setRunning) {
                    stateUpdater.setState(state.setIsRunning(setRunning.running()));
                } else if (intent instanceof ResetBoard resetBoard) {
                    stateUpdater.setState(State.initialState(resetBoard.random()));
                }
            }


            @Override
            public void onUpdated(ComponentCompositeKey componentId, State oldState, State newState, StateUpdater<State> stateUpdate) {
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
            public void onUnmounted(ComponentCompositeKey componentId, State state) {
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
                                      new StaticResources(new File("src/main/java/rsp/app/gameoflife"),
                                                         "/res/"));
        s.start();
        s.join();
    }
}
