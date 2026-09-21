package rsp.app.gameoflife;

import rsp.actor.SendResult;
import rsp.actor.ui.PageActorDirectory;
import rsp.actor.ui.UiActors;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentSegment;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.StateUpdater;
import rsp.component.definitions.Component;

import java.util.stream.IntStream;

import static rsp.dsl.Html.*;

/** Shared component definition; each page mount owns one game subscription. */
final class LifeComponent extends Component<State, LifeComponent.Intent> {
    sealed interface Intent permits Toggle, Control { }
    record Toggle(int x, int y) implements Intent { }
    record Control(LifeGame.Action action) implements Intent { }

    private final PageActorDirectory<Long, LifeGame.Command> games;

    LifeComponent(PageActorDirectory<Long, LifeGame.Command> games) {
        this.games = games;
    }

    @Override
    public ComponentStateSupplier<State> initStateSupplier() {
        return (_, _) -> State.loading();
    }

    @Override
    public ComponentView<State, Intent> componentView() {
        return intents -> state -> {
            if (state.snapshot().isEmpty()) {
                return html(head(title("Conway's Game of Life"),
                                link(attr("rel", "stylesheet"), attr("href", "/res/style.css"))),
                        body(div(attr("class", "game"), h1("Game of Life"), p("Connecting to game…"),
                                when(state.error().isPresent(), () -> p(attr("class", "error"),
                                        text(state.error().orElseThrow()))))));
            }
            LifeGame.Snapshot snapshot = state.snapshot().orElseThrow();
            Board board = snapshot.board();
            boolean running = snapshot.summary().status() == LifeGame.Phase.RUNNING;
            return html(head(title("Conway's Game of Life"),
                            link(attr("rel", "stylesheet"), attr("href", "/res/style.css"))),
                    body(div(attr("class", "game"),
                            h1("Game of Life"),
                            p("Game " + snapshot.summary().id() + " · "
                                    + snapshot.summary().status() + " · generation "
                                    + snapshot.summary().generation()),
                            when(state.error().isPresent(), () -> p(attr("class", "error"),
                                    text(state.error().orElseThrow()))),
                            div(attr("class", "board"),
                                    of(IntStream.range(0, board.size())
                                            .mapToObj(index -> div(attr("class", "c" + (board.isAlive(index) ? "1" : "0")),
                                                    when(!running, on("click", _ -> intents.dispatch(
                                                            new Toggle(Board.x(index), Board.y(index))))))))),
                            div(attr("class", "controls"),
                                    button(attr("type", "button"), when(running, () -> attr("disabled")),
                                            text("Start"), on("click", _ -> intents.dispatch(
                                                    new Control(LifeGame.Action.START)))),
                                    button(attr("type", "button"), when(!running, () -> attr("disabled")),
                                            text("Pause"), on("click", _ -> intents.dispatch(
                                                    new Control(LifeGame.Action.PAUSE)))),
                                    button(attr("type", "button"), when(running, () -> attr("disabled")),
                                            text("Clear"), on("click", _ -> intents.dispatch(
                                                    new Control(LifeGame.Action.RESET)))),
                                    button(attr("type", "button"), when(running, () -> attr("disabled")),
                                            text("Random"), on("click", _ -> intents.dispatch(
                                                    new Control(LifeGame.Action.RANDOM))))))));
        };
    }

    @Override
    protected void onIntent(Intent intent, State state, StateUpdater<State> updater) {
        LifeGame.Command command = switch (intent) {
            case Toggle toggle -> new LifeGame.ToggleCell(toggle.x(), toggle.y());
            case Control control -> LifeGame.Control.of(control.action());
        };
        SendResult result = state.snapshot()
                .flatMap(snapshot -> games.find(snapshot.summary().id()))
                .map(active -> active.ref().tell(command))
                .orElse(SendResult.STOPPED);
        if (result != SendResult.ACCEPTED) {
            updater.applyStateTransformation(current -> current.withError("Game unavailable: " + result));
        }
    }

    @Override
    public void onMounted(ComponentSegment<State> segment, ComponentCompositeKey componentId,
                          State state, CommandsEnqueue commandsEnqueue, StateUpdater<State> updater) {
        var game = games.forPage(componentId.sessionId(), segment);
        SendResult result = UiActors.observe(segment, updater, game.ref(), State::withSnapshot,
                LifeGame.Subscribe::new, LifeGame.Unsubscribe::new);
        if (result != SendResult.ACCEPTED) {
            updater.applyStateTransformation(current -> current.withError(
                    "Game unavailable: " + result));
        }
    }
}
