package rsp.actor;

import rsp.application.ApplicationLifecycle;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/** Lifecycle-owned registry of logical actor references. */
public interface ActorSystem extends ApplicationLifecycle {
    <M> ActorRef<M> ref(ActorId<M> id);

    <M, R> CompletionStage<R> ask(ActorRef<M> target,
                                  Function<ActorRef<R>, M> command,
                                  Duration timeout);

    /** Asks with caller-supplied delivery metadata (for example a request message ID). */
    <M, R> CompletionStage<R> askEnvelope(ActorRef<M> target,
                                          Function<ActorRef<R>,
                                          ActorEnvelope<M>> command,
                                          Duration timeout);

    CompletionStage<Void> drainAndStop();

    default <K, M> ActorRef<M> ref(ActorType<K, M> type, K key) {
        return ref(Objects.requireNonNull(type, "type").id(key));
    }
}
