package rsp.actor.runtime;

import rsp.actor.ActorId;
import rsp.actor.ActorRef;
import rsp.actor.SendResult;

/** Optional diagnostics; observer failures never alter actor delivery. */
public interface ActorSystemObserver {
    ActorSystemObserver NOOP = new ActorSystemObserver() { };

    default void systemStarted() { }

    default void systemStopping() { }

    default void systemStopped(Throwable failure) { }

    default void actorActivated(ActorId<?> id) { }

    default void messageRejected(ActorId<?> id, SendResult reason) { }

    default void outboundRejected(ActorId<?> sender, ActorRef<?> recipient, SendResult reason) { }

    default void messageProcessed(ActorId<?> id) { }

    default void actorFailed(ActorId<?> id, Throwable failure) { }
}
