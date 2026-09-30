package rsp.app.gameoflife;

import rsp.actor.ActorDefinition;
import rsp.actor.ui.ActorComponent;
import rsp.actor.ui.ActorComponentContext;
import rsp.actor.ui.PageActorDirectory;
import rsp.actor.ui.ActorBinding;
import rsp.actor.ui.ActorRenderPolicy;
import rsp.actor.runtime.ActorSnapshot;
import rsp.component.ComponentView;

import java.util.Objects;
import java.util.stream.IntStream;

import static rsp.dsl.Html.*;

/** One board/control view for page-owned and application-owned Life actors. */
final class LifeComponent extends ActorComponent<LifeGame.State, LifeGame.Command> {
    private final ActorBinding<LifeGame.State, LifeGame.Command> binding;
    private final ActorRenderPolicy renderPolicy;

    LifeComponent(PageActorDirectory<Long, LifeGame.Command> games,
                  ActorDefinition<LifeGame.State, LifeGame.Command> game) {
        this(ActorBinding.page(games, game), ActorRenderPolicy.immediate());
    }

    LifeComponent(ActorBinding<LifeGame.State, LifeGame.Command> binding, ActorRenderPolicy renderPolicy) {
        this.binding = Objects.requireNonNull(binding, "binding");
        this.renderPolicy = Objects.requireNonNull(renderPolicy, "renderPolicy");
    }

    @Override
    protected ActorBinding<LifeGame.State, LifeGame.Command> binding(ActorComponentContext context) {
        return binding;
    }

    @Override
    protected ActorRenderPolicy renderPolicy() {
        return renderPolicy;
    }

    @Override
    protected ComponentView<ActorSnapshot<LifeGame.State>, LifeGame.Command> snapshotView() {
        return commands -> snapshot -> view(snapshot.status()).resolve(commands).apply(snapshot.state());
    }

    @Override
    public ComponentView<LifeGame.State, LifeGame.Command> componentView() {
        return view(ActorSnapshot.Status.ACTIVE);
    }

    private ComponentView<LifeGame.State, LifeGame.Command> view(ActorSnapshot.Status lifecycle) {
        return commands -> state -> {
            Board board = state.board();
            boolean active = lifecycle == ActorSnapshot.Status.ACTIVE;
            boolean running = state.summary().status() == LifeGame.Phase.RUNNING;
            return html(head(title("Conway's Game of Life"),
                            link(attr("rel", "stylesheet"), attr("href", "/res/style.css"))),
                    body(div(attr("class", "game"),
                            h1("Game of Life"),
                            p("Game " + state.summary().id() + " · "
                                    + (active ? state.summary().status() : lifecycle) + " · generation "
                                    + state.summary().generation()),
                            div(attr("class", "board"),
                                    of(IntStream.range(0, board.size())
                                            .mapToObj(index -> div(attr("class", "c" + (board.isAlive(index) ? "1" : "0")),
                                                    when(active && !running, on("click", _ -> commands.dispatch(
                                                            new LifeGame.ToggleCell(
                                                                    Board.x(index), Board.y(index))))))))),
                            div(attr("class", "controls"),
                                    button(attr("type", "button"), when(!active || running, () -> attr("disabled")),
                                            text("Start"), on("click", _ -> commands.dispatch(
                                                    LifeGame.Control.of(LifeGame.Action.START)))),
                                    button(attr("type", "button"), when(!active || !running, () -> attr("disabled")),
                                            text("Pause"), on("click", _ -> commands.dispatch(
                                                    LifeGame.Control.of(LifeGame.Action.PAUSE)))),
                                    button(attr("type", "button"), when(!active || running, () -> attr("disabled")),
                                            text("Clear"), on("click", _ -> commands.dispatch(
                                                    LifeGame.Control.of(LifeGame.Action.RESET)))),
                                    button(attr("type", "button"), when(!active || running, () -> attr("disabled")),
                                            text("Random"), on("click", _ -> commands.dispatch(
                                                    LifeGame.Control.of(LifeGame.Action.RANDOM))))))));
        };
    }
}
