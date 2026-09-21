package rsp.actor.testkit;

import rsp.actor.ActorDeliveryException;
import rsp.actor.ActorEnvelope;
import rsp.actor.ActorRef;
import rsp.actor.ProcessingReceipt;
import rsp.actor.SendResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Thread-safe recording destination for actor messages. */
public final class ActorProbe<M> implements ActorRef<M>, AutoCloseable {
    private final List<ActorEnvelope<M>> received = new ArrayList<>();
    private boolean closed;

    @Override
    public synchronized SendResult tell(ActorEnvelope<M> envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (closed) {
            return SendResult.STOPPED;
        }
        received.add(envelope);
        return SendResult.ACCEPTED;
    }

    @Override
    public ProcessingReceipt track(ActorEnvelope<M> envelope) {
        SendResult result = tell(envelope);
        return new ProcessingReceipt(result, result == SendResult.ACCEPTED
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.failedFuture(new ActorDeliveryException(result)));
    }

    public synchronized List<M> messages() {
        return received.stream().map(ActorEnvelope::message).toList();
    }

    public synchronized List<ActorEnvelope<M>> envelopes() {
        return List.copyOf(received);
    }

    @Override
    public synchronized void close() {
        closed = true;
    }
}
