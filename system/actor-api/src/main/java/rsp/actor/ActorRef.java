package rsp.actor;

import java.util.Optional;

/** Message-only capability. Implementations must never expose an actor instance. */
public interface ActorRef<M> {
    SendResult tell(ActorEnvelope<M> envelope);

    ProcessingReceipt track(ActorEnvelope<M> envelope);

    default SendResult tell(M message) {
        return tell(ActorEnvelope.of(message));
    }

    default ProcessingReceipt track(M message) {
        return track(ActorEnvelope.of(message));
    }

    /** Temporary reply references may have no stable actor ID. */
    default Optional<ActorId<M>> id() {
        return Optional.empty();
    }
}
