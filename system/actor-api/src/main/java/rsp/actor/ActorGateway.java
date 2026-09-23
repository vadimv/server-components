package rsp.actor;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Host-neutral ask/reply capability for actor references.
 * <p>
 * A gateway does not imply that it owns or can resolve the target actor. It
 * supplies the temporary reply reference and timeout machinery around an
 * already-resolved {@link ActorRef}.
 */
public interface ActorGateway {
    <M, R> CompletionStage<R> ask(ActorRef<M> target,
                                  Function<ActorRef<R>, M> command,
                                  Duration timeout);

    /** Asks with caller-supplied delivery metadata. */
    <M, R> CompletionStage<R> askEnvelope(ActorRef<M> target,
                                          Function<ActorRef<R>, ActorEnvelope<M>> command,
                                          Duration timeout);
}
