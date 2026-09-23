package rsp.actor;

import rsp.application.ApplicationLifecycle;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Lifecycle-owned registry of logical actor references. */
public interface ActorSystem extends ActorGateway, ApplicationLifecycle {
    <M> ActorRef<M> ref(ActorId<M> id);

    CompletionStage<Void> drainAndStop();

    default <K, M> ActorRef<M> ref(ActorType<K, M> type, K key) {
        return ref(Objects.requireNonNull(type, "type").id(key));
    }
}
