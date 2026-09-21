package rsp.actor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable state transition and outbound messages; no effect is applied on behavior failure. */
public final class ActorEffect<S> {
    private final boolean stateChanged;
    private final S state;
    private final List<Delivery<?>> deliveries;
    private final boolean stop;
    private final boolean passivate;

    private ActorEffect(boolean stateChanged, S state, List<Delivery<?>> deliveries,
                        boolean stop, boolean passivate) {
        this.stateChanged = stateChanged;
        this.state = state;
        this.deliveries = List.copyOf(deliveries);
        this.stop = stop;
        this.passivate = passivate;
    }

    public static <S> ActorEffect<S> same() {
        return new ActorEffect<>(false, null, List.of(), false, false);
    }

    public static <S> ActorEffect<S> state(S state) {
        return new ActorEffect<>(true, Objects.requireNonNull(state, "state"), List.of(), false, false);
    }

    public ActorEffect<S> withState(S value) {
        return new ActorEffect<>(true, Objects.requireNonNull(value, "state"), deliveries, stop, passivate);
    }

    public <M> ActorEffect<S> send(ActorRef<M> recipient, M message) {
        return sendEnvelope(recipient, ActorEnvelope.of(message));
    }

    /** Preserves caller-supplied message, correlation, and causation IDs. */
    public <M> ActorEffect<S> sendEnvelope(ActorRef<M> recipient, ActorEnvelope<M> envelope) {
        return scheduleEnvelope(recipient, envelope, Duration.ZERO);
    }

    public <M> ActorEffect<S> reply(ActorRef<M> recipient, M message) {
        return send(recipient, message);
    }

    public <M> ActorEffect<S> schedule(ActorRef<M> recipient, M message, Duration delay) {
        return scheduleEnvelope(recipient, ActorEnvelope.of(message), delay);
    }

    public <M> ActorEffect<S> scheduleEnvelope(ActorRef<M> recipient,
                                               ActorEnvelope<M> envelope, Duration delay) {
        Delivery<M> delivery = new Delivery<>(recipient, envelope, delay);
        List<Delivery<?>> copy = new ArrayList<>(deliveries);
        copy.add(delivery);
        return new ActorEffect<>(stateChanged, state, copy, stop, passivate);
    }

    public ActorEffect<S> stopping() {
        return new ActorEffect<>(stateChanged, state, deliveries, true, false);
    }

    /** Stops and releases this local actor incarnation after its accepted work is settled. */
    public ActorEffect<S> passivating() {
        return new ActorEffect<>(stateChanged, state, deliveries, true, true);
    }

    public boolean stateChanged() {
        return stateChanged;
    }

    public S state() {
        if (!stateChanged) {
            throw new IllegalStateException("Effect does not replace state");
        }
        return state;
    }

    public List<Delivery<?>> deliveries() {
        return deliveries;
    }

    public boolean stopsActor() {
        return stop;
    }

    public boolean passivatesActor() {
        return passivate;
    }

    /** A local, best-effort send after state replacement; delay zero means immediate. */
    public record Delivery<M>(ActorRef<M> recipient, ActorEnvelope<M> envelope, Duration delay) {
        public Delivery {
            Objects.requireNonNull(recipient, "recipient");
            Objects.requireNonNull(envelope, "envelope");
            if (Objects.requireNonNull(delay, "delay").isNegative()) {
                throw new IllegalArgumentException("delay must not be negative");
            }
        }
    }
}
