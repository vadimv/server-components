package rsp.app.gameoflife;

import rsp.actor.ActorBehavior;
import rsp.actor.ActorDefinition;
import rsp.actor.ActorEffect;
import rsp.actor.ActorRef;
import rsp.actor.ActorType;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** One in-memory Game of Life actor per page session, with a UI-independent message protocol. */
public final class LifeGame {
    public static final ActorType<Long, Command> TYPE =
            ActorType.named("life-game", Command.class, String::valueOf);
    public static final Duration TICK_INTERVAL = Duration.ofMillis(150);

    private LifeGame() {
    }

    public enum Phase { READY, RUNNING, PAUSED }

    public enum Action { START, PAUSE, RESET, RANDOM }

    public record GameSummary(long id, String kind, Phase status, long generation) { }

    /** Authoritative state rendered directly by an actor component. */
    public record State(GameSummary summary, Board board, long epoch) {
        public State {
            Objects.requireNonNull(summary, "summary");
            Objects.requireNonNull(board, "board");
        }
    }

    public sealed interface Command permits Control, ToggleCell, Status, Tick { }

    public record Control(Action action, Optional<ActorRef<GameSummary>> replyTo) implements Command {
        public Control {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(replyTo, "replyTo");
        }

        public static Control of(Action action) {
            return new Control(action, Optional.empty());
        }

        public static Control replying(Action action, ActorRef<GameSummary> replyTo) {
            return new Control(action, Optional.of(replyTo));
        }
    }

    public record ToggleCell(int x, int y) implements Command { }

    public record Status(ActorRef<GameSummary> replyTo) implements Command {
        public Status {
            Objects.requireNonNull(replyTo, "replyTo");
        }
    }

    /** Epoch invalidates ticks already scheduled before pause or reset. */
    public record Tick(long epoch) implements Command { }

    public static ActorDefinition<State, Command> definition(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        return ActorDefinition.<State, Command>builder(TYPE)
                .initialState(id -> new State(
                        new GameSummary(Long.parseLong(id.key()), "life", Phase.READY, 0),
                        Board.empty(), 0))
                .behavior(ActorBehavior.sync((context, state, command) -> {
                    return switch (command) {
                        case Status status -> ActorEffect.<State>same()
                                .reply(status.replyTo(), state.summary());
                        case ToggleCell toggle -> {
                            if (state.summary().status() == Phase.RUNNING
                                    || !state.board().contains(toggle.x(), toggle.y())) {
                                yield ActorEffect.same();
                            }
                            yield ActorEffect.state(new State(state.summary(),
                                    state.board().toggle(toggle.x(), toggle.y()), state.epoch()));
                        }
                        case Control control -> control(context.self(), state, control, random);
                        case Tick tick -> {
                            if (state.summary().status() != Phase.RUNNING
                                    || tick.epoch() != state.epoch()) {
                                yield ActorEffect.same();
                            }
                            State next = with(state, state.board().advance(), Phase.RUNNING,
                                    state.summary().generation() + 1, state.epoch());
                            yield ActorEffect.state(next).schedule(
                                    context.self(), new Tick(next.epoch()), TICK_INTERVAL);
                        }
                    };
                }))
                .mailboxCapacity(512)
                .build();
    }

    private static ActorEffect<State> control(ActorRef<Command> self, State state,
                                               Control control, RandomGenerator random) {
        State next = switch (control.action()) {
            case START -> state.summary().status() == Phase.RUNNING ? state
                    : with(state, state.board(), Phase.RUNNING,
                            state.summary().generation(), state.epoch() + 1);
            case PAUSE -> state.summary().status() != Phase.RUNNING ? state
                    : with(state, state.board(), Phase.PAUSED,
                            state.summary().generation(), state.epoch() + 1);
            case RESET -> with(state, Board.empty(), Phase.READY, 0, state.epoch() + 1);
            case RANDOM -> with(state, Board.random(random), Phase.READY, 0, state.epoch() + 1);
        };
        ActorEffect<State> effect = next == state ? ActorEffect.same() : ActorEffect.state(next);
        if (next != state && next.summary().status() == Phase.RUNNING) {
            effect = effect.schedule(self, new Tick(next.epoch()), TICK_INTERVAL);
        }
        if (control.replyTo().isPresent()) {
            effect = effect.reply(control.replyTo().orElseThrow(), next.summary());
        }
        return effect;
    }

    private static State with(State state, Board board, Phase phase,
                              long generation, long epoch) {
        GameSummary current = state.summary();
        return new State(new GameSummary(current.id(), current.kind(), phase, generation),
                board, epoch);
    }
}
