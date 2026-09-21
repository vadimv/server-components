package rsp.actor;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Processes one message; effects are applied only after successful completion. */
@FunctionalInterface
public interface ActorBehavior<S, M> {
    CompletionStage<ActorEffect<S>> receive(ActorContext<M> context, S state, M message);

    static <S, M> ActorBehavior<S, M> sync(Synchronous<S, M> behavior) {
        Objects.requireNonNull(behavior, "behavior");
        return (context, state, message) -> CompletableFuture.completedFuture(
                Objects.requireNonNull(behavior.receive(context, state, message), "actor effect"));
    }

    @FunctionalInterface
    interface Synchronous<S, M> {
        ActorEffect<S> receive(ActorContext<M> context, S state, M message);
    }
}
