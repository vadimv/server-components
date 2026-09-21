package rsp.actor;

import java.util.Objects;
import java.util.function.Function;

/** One reusable actor behavior with an initializer for keyed activations. */
public final class ActorDefinition<S, M> {
    private final ActorType<?, M> type;
    private final Function<ActorId<M>, S> initialState;
    private final ActorBehavior<S, M> behavior;
    private final int mailboxCapacity;

    private ActorDefinition(Builder<S, M> builder) {
        type = builder.type;
        initialState = Objects.requireNonNull(builder.initialState, "initialState");
        behavior = Objects.requireNonNull(builder.behavior, "behavior");
        mailboxCapacity = builder.mailboxCapacity;
    }

    public static <S, M> Builder<S, M> builder(ActorType<?, M> type) {
        return new Builder<>(type);
    }

    public ActorType<?, M> type() {
        return type;
    }

    public S initialState(ActorId<M> id) {
        return Objects.requireNonNull(initialState.apply(id), "initial actor state");
    }

    public ActorBehavior<S, M> behavior() {
        return behavior;
    }

    public int mailboxCapacity() {
        return mailboxCapacity;
    }

    public static final class Builder<S, M> {
        private final ActorType<?, M> type;
        private Function<ActorId<M>, S> initialState;
        private ActorBehavior<S, M> behavior;
        private int mailboxCapacity = 256;

        private Builder(ActorType<?, M> type) {
            this.type = Objects.requireNonNull(type, "type");
        }

        public Builder<S, M> initialState(Function<ActorId<M>, S> value) {
            initialState = Objects.requireNonNull(value, "initialState");
            return this;
        }

        public Builder<S, M> behavior(ActorBehavior<S, M> value) {
            behavior = Objects.requireNonNull(value, "behavior");
            return this;
        }

        public Builder<S, M> mailboxCapacity(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("mailboxCapacity must be positive");
            }
            mailboxCapacity = value;
            return this;
        }

        public ActorDefinition<S, M> build() {
            return new ActorDefinition<>(this);
        }
    }
}
