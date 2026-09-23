package rsp.app.gameoflife;

import rsp.actor.ActorDefinition;
import rsp.actor.ui.ActorComponent;
import rsp.actor.ui.ActorComponentContext;
import rsp.actor.ui.PageActorDirectory;
import rsp.actor.ui.PageActorPlacement;
import rsp.component.ComponentView;

import java.util.Objects;
import java.util.stream.IntStream;

import static rsp.dsl.Html.*;

/** Shared definition whose page-hosted actor owns the authoritative state. */
final class LifeComponent extends ActorComponent<LifeGame.State, LifeGame.Command> {
    private final PageActorDirectory<Long, LifeGame.Command> games;
    private final ActorDefinition<LifeGame.State, LifeGame.Command> game;

    LifeComponent(PageActorDirectory<Long, LifeGame.Command> games,
                  ActorDefinition<LifeGame.State, LifeGame.Command> game) {
        this.games = Objects.requireNonNull(games, "games");
        this.game = Objects.requireNonNull(game, "game");
    }

    @Override
    protected ActorDefinition<LifeGame.State, LifeGame.Command> definition() {
        return game;
    }

    @Override
    protected PageActorPlacement<LifeGame.Command> placement(
            ActorComponentContext context) {
        return context.in(games);
    }

    @Override
    public ComponentView<LifeGame.State, LifeGame.Command> componentView() {
        return commands -> state -> {
            Board board = state.board();
            boolean running = state.summary().status() == LifeGame.Phase.RUNNING;
            return html(head(title("Conway's Game of Life"),
                            link(attr("rel", "stylesheet"), attr("href", "/res/style.css"))),
                    body(div(attr("class", "game"),
                            h1("Game of Life"),
                            p("Game " + state.summary().id() + " · "
                                    + state.summary().status() + " · generation "
                                    + state.summary().generation()),
                            div(attr("class", "board"),
                                    of(IntStream.range(0, board.size())
                                            .mapToObj(index -> div(attr("class", "c" + (board.isAlive(index) ? "1" : "0")),
                                                    when(!running, on("click", _ -> commands.dispatch(
                                                            new LifeGame.ToggleCell(
                                                                    Board.x(index), Board.y(index))))))))),
                            div(attr("class", "controls"),
                                    button(attr("type", "button"), when(running, () -> attr("disabled")),
                                            text("Start"), on("click", _ -> commands.dispatch(
                                                    LifeGame.Control.of(LifeGame.Action.START)))),
                                    button(attr("type", "button"), when(!running, () -> attr("disabled")),
                                            text("Pause"), on("click", _ -> commands.dispatch(
                                                    LifeGame.Control.of(LifeGame.Action.PAUSE)))),
                                    button(attr("type", "button"), when(running, () -> attr("disabled")),
                                            text("Clear"), on("click", _ -> commands.dispatch(
                                                    LifeGame.Control.of(LifeGame.Action.RESET)))),
                                    button(attr("type", "button"), when(running, () -> attr("disabled")),
                                            text("Random"), on("click", _ -> commands.dispatch(
                                                    LifeGame.Control.of(LifeGame.Action.RANDOM))))))));
        };
    }
}
