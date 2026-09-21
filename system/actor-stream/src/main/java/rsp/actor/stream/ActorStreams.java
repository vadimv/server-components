package rsp.actor.stream;

import rsp.actor.ActorEnvelope;
import rsp.actor.ActorRef;
import rsp.actor.MessageId;
import rsp.stream.StreamDelivery;
import rsp.stream.StreamSource;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Transport-neutral stream ingress for local or future remote actor refs. */
public final class ActorStreams {
    private ActorStreams() {
    }

    /**
     * Consumes records one at a time. The stable stream record ID becomes the
     * actor envelope's message ID; this carries identity but does not dedupe.
     */
    public static <E, M> ActorStreamBinding<E, M> consume(
            StreamSource<E> source,
            Function<? super StreamDelivery<E>, ? extends ActorRef<M>> target,
            Function<? super StreamDelivery<E>, ? extends M> command) {
        Objects.requireNonNull(command, "command");
        ActorStreamBinding<E, M> binding = consumer(source, target, command);
        binding.start();
        return binding;
    }

    /** Creates a deferred binding for application-lifecycle registration. */
    public static <E, M> ActorStreamBinding<E, M> consumer(
            StreamSource<E> source,
            Function<? super StreamDelivery<E>, ? extends ActorRef<M>> target,
            Function<? super StreamDelivery<E>, ? extends M> command) {
        Objects.requireNonNull(command, "command");
        return consumerEnvelopes(source, target, delivery -> new ActorEnvelope<>(
                new MessageId(delivery.id().value()),
                Objects.requireNonNull(command.apply(delivery), "stream command"),
                Optional.empty(), Optional.empty()));
    }

    /** Uses a caller-built envelope when correlation or causation metadata is needed. */
    public static <E, M> ActorStreamBinding<E, M> consumeEnvelopes(
            StreamSource<E> source,
            Function<? super StreamDelivery<E>, ? extends ActorRef<M>> target,
            Function<? super StreamDelivery<E>, ? extends ActorEnvelope<M>> envelope) {
        ActorStreamBinding<E, M> binding = consumerEnvelopes(source, target, envelope);
        binding.start();
        return binding;
    }

    /** Deferred variant with caller-supplied actor envelopes. */
    public static <E, M> ActorStreamBinding<E, M> consumerEnvelopes(
            StreamSource<E> source,
            Function<? super StreamDelivery<E>, ? extends ActorRef<M>> target,
            Function<? super StreamDelivery<E>, ? extends ActorEnvelope<M>> envelope) {
        return new ActorStreamBinding<>(source, target, envelope);
    }
}
