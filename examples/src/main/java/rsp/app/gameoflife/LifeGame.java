package rsp.app.gameoflife;

import rsp.actor.ActorBehavior;
import rsp.actor.ActorDefinition;
import rsp.actor.ActorEffect;
import rsp.actor.ActorRef;
import rsp.actor.ActorType;

import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    /** Full, immutable projection suitable for coalescing by slow UI subscribers. */
    public record Snapshot(GameSummary summary, Board board) { }

    public sealed interface Command permits Control, ToggleCell, Subscribe, Unsubscribe, Status, Tick, Close { }

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

    public record Subscribe(ActorRef<Snapshot> subscriber) implements Command {
        public Subscribe {
            Objects.requireNonNull(subscriber, "subscriber");
        }
    }

    public record Unsubscribe(ActorRef<Snapshot> subscriber) implements Command {
        public Unsubscribe {
            Objects.requireNonNull(subscriber, "subscriber");
        }
    }

    public record Status(ActorRef<GameSummary> replyTo) implements Command {
        public Status {
            Objects.requireNonNull(replyTo, "replyTo");
        }
    }

    /** Epoch invalidates ticks already scheduled before pause or reset. */
    public record Tick(long epoch) implements Command { }

    /** The owning page has closed; release this actor and its scheduled ticks. */
    public record Close() implements Command { }

    private record GameState(Board board, Phase phase, long generation, long epoch,
                             Set<ActorRef<Snapshot>> subscribers) {
        private GameState {
            subscribers = Set.copyOf(subscribers);
        }

        private GameSummary summary(long id) {
            return new GameSummary(id, "life", phase, generation);
        }

        private Snapshot snapshot(long id) {
            return new Snapshot(summary(id), board);
        }
    }

    public static ActorDefinition<?, Command> definition(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        return ActorDefinition.<GameState, Command>builder(TYPE)
                .initialState(_ -> new GameState(Board.empty(), Phase.READY, 0, 0, Set.of()))
                .behavior(ActorBehavior.sync((context, state, command) -> {
                    long id = Long.parseLong(context.id().key());
                    return switch (command) {
                        case Close _ -> ActorEffect.<GameState>same().passivating();
                        case Subscribe subscribe -> {
                            Set<ActorRef<Snapshot>> subscribers = new HashSet<>(state.subscribers());
                            subscribers.add(subscribe.subscriber());
                            GameState next = new GameState(state.board(), state.phase(),
                                    state.generation(), state.epoch(), subscribers);
                            yield ActorEffect.<GameState>state(next)
                                    .send(subscribe.subscriber(), next.snapshot(id));
                        }
                        case Unsubscribe unsubscribe -> {
                            Set<ActorRef<Snapshot>> subscribers = new HashSet<>(state.subscribers());
                            subscribers.remove(unsubscribe.subscriber());
                            yield ActorEffect.state(new GameState(state.board(), state.phase(),
                                    state.generation(), state.epoch(), subscribers));
                        }
                        case Status status -> ActorEffect.<GameState>same()
                                .reply(status.replyTo(), state.summary(id));
                        case ToggleCell toggle -> {
                            if (state.phase() == Phase.RUNNING || !state.board().contains(toggle.x(), toggle.y())) {
                                yield ActorEffect.same();
                            }
                            yield publish(new GameState(state.board().toggle(toggle.x(), toggle.y()),
                                    state.phase(), state.generation(), state.epoch(), state.subscribers()), id);
                        }
                        case Control control -> control(context.self(), state, control, id, random);
                        case Tick tick -> {
                            if (state.phase() != Phase.RUNNING || tick.epoch() != state.epoch()) {
                                yield ActorEffect.same();
                            }
                            GameState next = new GameState(state.board().advance(), state.phase(),
                                    state.generation() + 1, state.epoch(), state.subscribers());
                            yield publish(next, id).schedule(context.self(), new Tick(next.epoch()), TICK_INTERVAL);
                        }
                    };
                }))
                .mailboxCapacity(512)
                .build();
    }

    private static ActorEffect<GameState> control(ActorRef<Command> self, GameState state,
                                                   Control control, long id, RandomGenerator random) {
        GameState next = switch (control.action()) {
            case START -> state.phase() == Phase.RUNNING ? state
                    : new GameState(state.board(), Phase.RUNNING, state.generation(),
                            state.epoch() + 1, state.subscribers());
            case PAUSE -> state.phase() != Phase.RUNNING ? state
                    : new GameState(state.board(), Phase.PAUSED, state.generation(),
                            state.epoch() + 1, state.subscribers());
            case RESET -> new GameState(Board.empty(), Phase.READY, 0,
                    state.epoch() + 1, state.subscribers());
            case RANDOM -> new GameState(Board.random(random), Phase.READY, 0,
                    state.epoch() + 1, state.subscribers());
        };
        ActorEffect<GameState> effect = next == state ? ActorEffect.same() : publish(next, id);
        if (next != state && next.phase() == Phase.RUNNING) {
            effect = effect.schedule(self, new Tick(next.epoch()), TICK_INTERVAL);
        }
        if (control.replyTo().isPresent()) {
            effect = effect.reply(control.replyTo().orElseThrow(), next.summary(id));
        }
        return effect;
    }

    private static ActorEffect<GameState> publish(GameState next, long id) {
        ActorEffect<GameState> effect = ActorEffect.state(next);
        Snapshot snapshot = next.snapshot(id);
        for (ActorRef<Snapshot> subscriber : next.subscribers()) {
            effect = effect.send(subscriber, snapshot);
        }
        return effect;
    }
}
