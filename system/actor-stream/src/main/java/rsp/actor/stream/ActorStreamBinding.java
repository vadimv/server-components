package rsp.actor.stream;

import rsp.application.ApplicationLifecycle;
import rsp.actor.ActorDeliveryException;
import rsp.actor.ActorEnvelope;
import rsp.actor.ActorRef;
import rsp.actor.ProcessingReceipt;
import rsp.actor.SendResult;
import rsp.stream.StreamDelivery;
import rsp.stream.StreamSource;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Function;

/**
 * One source subscription forwarding records to actors. It asks for one record
 * at a time and acknowledges only after tracked actor processing and the
 * connector's acknowledgment complete. The application owns and closes it
 * before stopping the source or actor system.
 */
public final class ActorStreamBinding<E, M>
        implements Flow.Subscriber<StreamDelivery<E>>, ApplicationLifecycle {
    private final Object lock = new Object();
    private final StreamSource<E> source;
    private final Function<? super StreamDelivery<E>, ? extends ActorRef<M>> target;
    private final Function<? super StreamDelivery<E>, ? extends ActorEnvelope<M>> envelope;
    private final CompletableFuture<Void> completion = new CompletableFuture<>();

    private Flow.Subscription subscription;
    private boolean started;
    private boolean inFlight;
    private boolean settling;
    private boolean sourceComplete;
    private boolean closed;
    private boolean demandOutstanding;
    private boolean requesting;
    private boolean requestPending;

    ActorStreamBinding(StreamSource<E> source,
                       Function<? super StreamDelivery<E>, ? extends ActorRef<M>> target,
                       Function<? super StreamDelivery<E>, ? extends ActorEnvelope<M>> envelope) {
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
        this.envelope = Objects.requireNonNull(envelope, "envelope");
    }

    /** Completes after source completion and final settlement, or exceptionally on failure. */
    public CompletionStage<Void> completion() {
        return completion;
    }

    /** Subscribes once; register after the actor system in {@code ApplicationContext}. */
    @Override
    public void start() {
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("Actor stream binding is closed");
            }
            if (started) {
                return;
            }
            started = true;
        }
        try {
            source.subscribe(this);
        } catch (RuntimeException | Error failure) {
            fail(failure);
            throw failure;
        }
    }

    @Override
    public void stop() {
        close();
    }

    @Override
    public void onSubscribe(Flow.Subscription next) {
        Objects.requireNonNull(next, "subscription");
        boolean accepted;
        synchronized (lock) {
            accepted = !closed && subscription == null;
            if (accepted) {
                subscription = next;
            }
        }
        if (!accepted) {
            next.cancel();
            return;
        }
        requestOne();
    }

    @Override
    public void onNext(StreamDelivery<E> delivery) {
        if (delivery == null) {
            fail(new NullPointerException("stream delivery"));
            return;
        }
        boolean invalidDemand;
        synchronized (lock) {
            if (closed) {
                return;
            }
            invalidDemand = subscription == null || sourceComplete || inFlight || !demandOutstanding;
            if (!invalidDemand) {
                demandOutstanding = false;
                inFlight = true;
                settling = false;
            }
        }
        if (invalidDemand) {
            fail(new IllegalStateException("Source emitted without outstanding demand"));
            return;
        }
        try {
            ActorRef<M> recipient = Objects.requireNonNull(target.apply(delivery), "target actor");
            ActorEnvelope<M> message = Objects.requireNonNull(envelope.apply(delivery), "actor envelope");
            ProcessingReceipt receipt = Objects.requireNonNull(recipient.track(message), "processing receipt");
            if (receipt.admission() != SendResult.ACCEPTED) {
                settle(delivery, new ActorDeliveryException(receipt.admission()));
            } else {
                receipt.processed().whenComplete((_, failure) -> settle(delivery, failure));
            }
        } catch (Throwable failure) {
            settle(delivery, failure);
        }
    }

    @Override
    public void onError(Throwable failure) {
        fail(Objects.requireNonNull(failure, "source failure"));
    }

    @Override
    public void onComplete() {
        boolean finished;
        synchronized (lock) {
            if (closed) {
                return;
            }
            sourceComplete = true;
            finished = !inFlight;
            if (finished) {
                closed = true;
            }
        }
        if (finished) {
            completion.complete(null);
        }
    }

    @Override
    public void close() {
        Flow.Subscription current;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            current = subscription;
        }
        if (current != null) {
            try {
                current.cancel();
            } catch (Throwable failure) {
                completion.completeExceptionally(failure);
                return;
            }
        }
        completion.complete(null);
    }

    private void settle(StreamDelivery<E> delivery, Throwable failure) {
        synchronized (lock) {
            if (closed || !inFlight || settling) {
                return;
            }
            settling = true;
        }
        final CompletionStage<Void> settlement;
        try {
            settlement = Objects.requireNonNull(failure == null
                    ? delivery.acknowledge()
                    : delivery.negativeAcknowledge(failure), "stream settlement");
        } catch (Throwable settlementFailure) {
            fail(settlementFailure);
            return;
        }
        try {
            settlement.whenComplete((_, settlementFailure) -> {
                if (settlementFailure != null) {
                    fail(settlementFailure);
                    return;
                }
                boolean finished;
                synchronized (lock) {
                    if (closed) {
                        return;
                    }
                    inFlight = false;
                    settling = false;
                    finished = sourceComplete;
                    if (finished) {
                        closed = true;
                    }
                }
                if (finished) {
                    completion.complete(null);
                } else {
                    requestOne();
                }
            });
        } catch (Throwable registrationFailure) {
            fail(registrationFailure);
        }
    }

    /** Iterative request pump tolerates synchronous publishers and completed stages. */
    private void requestOne() {
        synchronized (lock) {
            if (closed || sourceComplete) {
                return;
            }
            requestPending = true;
            if (requesting) {
                return;
            }
            requesting = true;
        }
        while (true) {
            final Flow.Subscription current;
            synchronized (lock) {
                if (closed || sourceComplete || !requestPending) {
                    requesting = false;
                    return;
                }
                requestPending = false;
                current = subscription;
                demandOutstanding = true;
            }
            try {
                current.request(1);
            } catch (Throwable failure) {
                fail(failure);
                return;
            }
        }
    }

    private void fail(Throwable failure) {
        Flow.Subscription current;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            current = subscription;
        }
        if (current != null) {
            try {
                current.cancel();
            } catch (Throwable cancelFailure) {
                failure.addSuppressed(cancelFailure);
            }
        }
        completion.completeExceptionally(failure);
    }
}
