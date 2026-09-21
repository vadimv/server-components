package rsp.actor;

import java.util.Objects;
import java.util.Optional;

/** Immutable delivery metadata. Local delivery is at-most-once, not durable. */
public record ActorEnvelope<M>(MessageId messageId, M message,
                               Optional<MessageId> correlationId,
                               Optional<MessageId> causationId) {
    public ActorEnvelope {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(causationId, "causationId");
    }

    public static <M> ActorEnvelope<M> of(M message) {
        return new ActorEnvelope<>(MessageId.random(), message, Optional.empty(), Optional.empty());
    }
}
