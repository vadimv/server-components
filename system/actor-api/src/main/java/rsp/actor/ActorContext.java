package rsp.actor;

import java.util.Objects;

/** Read-only context supplied to one behavior invocation. */
public record ActorContext<M>(ActorId<M> id, ActorRef<M> self, ActorEnvelope<M> envelope) {
    public ActorContext {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(envelope, "envelope");
    }
}
