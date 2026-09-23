package rsp.actor.runtime;

import rsp.actor.ActorBehavior;
import rsp.actor.ActorContext;
import rsp.actor.ActorDefinition;
import rsp.actor.ActorDeliveryException;
import rsp.actor.ActorEffect;
import rsp.actor.ActorEnvelope;
import rsp.actor.ActorId;
import rsp.actor.ActorRef;
import rsp.actor.ProcessingReceipt;
import rsp.actor.SendResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Host-independent serialized actor activation.
 * <p>
 * The host chooses where turns and completions run and how effects are
 * delivered. This class owns the bounded mailbox, one-in-flight invariant,
 * state, receipts, and stop/passivation semantics shared by local and page
 * actors.
 */
public final class SerializedActorActivation<S, M> {
    private static final System.Logger logger =
            System.getLogger(SerializedActorActivation.class.getName());

    /** Host operations whose implementations must not call back while holding host locks. */
    public interface Host {
        SendResult admission(ActorId<?> id, boolean internal);

        void execute(ActorId<?> id, Runnable task);

        void accepted(ActorId<?> id);

        void settled(ActorId<?> id);

        void dispatch(ActorId<?> sender, ActorEffect.Delivery<?> delivery);

        default void activated(ActorId<?> id) {
        }

        default void rejected(ActorId<?> id, SendResult reason) {
        }

        default void processed(ActorId<?> id) {
        }

        default void failed(ActorId<?> id, Throwable failure) {
        }

        /** Called once when this activation stops, fails, closes, or passivates. */
        void terminated(ActorId<?> id, SerializedActorActivation<?, ?> activation,
                        boolean release);
    }

    private final ActorId<M> id;
    private final ActorDefinition<S, M> definition;
    private final Host host;
    private final ActivationRef ref = new ActivationRef();
    private final Deque<Pending<M>> mailbox = new ArrayDeque<>();
    private final List<Consumer<S>> stateObservers = new ArrayList<>();

    private S currentState;
    private boolean initialized;
    private boolean inFlight;
    private boolean scheduled;
    private boolean stopped;
    private boolean terminated;
    private Pending<M> current;

    public SerializedActorActivation(ActorId<M> id,
                                     ActorDefinition<S, M> definition,
                                     Host host) {
        this.id = Objects.requireNonNull(id, "id");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.host = Objects.requireNonNull(host, "host");
        if (!definition.type().equals(id.type())) {
            throw new IllegalArgumentException("Definition type does not match actor ID");
        }
    }

    public ActorId<M> id() {
        return id;
    }

    public ActorRef<M> ref() {
        return ref;
    }

    /** Initializes this activation without processing a message. Idempotent. */
    public S initialize() {
        final S initializedState;
        synchronized (this) {
            if (stopped) {
                throw new IllegalStateException("Actor is stopped: " + id);
            }
            if (initialized) {
                return currentState;
            }
            initializedState = definition.initialState(id);
            currentState = initializedState;
            initialized = true;
        }
        observe(() -> host.activated(id));
        return initializedState;
    }

    public synchronized S currentState() {
        if (!initialized) {
            throw new IllegalStateException("Actor is not initialized: " + id);
        }
        return currentState;
    }

    /**
     * Observes committed immutable state snapshots. Observer failure is
     * isolated from actor processing. The current snapshot is offered
     * immediately when already initialized.
     */
    public AutoCloseable observeState(Consumer<S> observer) {
        Objects.requireNonNull(observer, "observer");
        final S snapshot;
        synchronized (this) {
            if (stopped) {
                throw new IllegalStateException("Actor is stopped: " + id);
            }
            stateObservers.add(observer);
            snapshot = initialized ? currentState : null;
        }
        if (snapshot != null) {
            notifyObserver(observer, snapshot);
        }
        return () -> {
            synchronized (SerializedActorActivation.this) {
                stateObservers.removeIf(candidate -> candidate == observer);
            }
        };
    }

    /** Offers a message emitted by this host while it is draining. */
    public ProcessingReceipt offerInternal(ActorEnvelope<M> envelope) {
        return offer(envelope, true);
    }

    /** Administratively closes and releases this activation. */
    public void close(Throwable reason) {
        fail(Objects.requireNonNull(reason, "reason"), true, false);
    }

    private ProcessingReceipt offer(ActorEnvelope<M> envelope, boolean internal) {
        Objects.requireNonNull(envelope, "envelope");
        if (!id.type().messageClass().isInstance(envelope.message())) {
            throw new IllegalArgumentException(
                    "Message is not a " + id.type().messageClass().getName());
        }

        SendResult hostAdmission = Objects.requireNonNull(
                host.admission(id, internal), "host admission");
        if (hostAdmission != SendResult.ACCEPTED) {
            return rejected(hostAdmission);
        }

        CompletableFuture<Void> processed = new CompletableFuture<>();
        boolean enqueueRunner = false;
        synchronized (this) {
            if (stopped) {
                return rejected(SendResult.STOPPED);
            }
            if (mailbox.size() >= definition.mailboxCapacity()) {
                return rejected(SendResult.MAILBOX_FULL);
            }
            host.accepted(id);
            mailbox.addLast(new Pending<>(envelope, processed));
            if (!inFlight && !scheduled) {
                scheduled = true;
                enqueueRunner = true;
            }
        }
        if (enqueueRunner) {
            execute(this::processNext);
        }
        return new ProcessingReceipt(SendResult.ACCEPTED, processed);
    }

    private ProcessingReceipt rejected(SendResult result) {
        observe(() -> host.rejected(id, result));
        return new ProcessingReceipt(result,
                CompletableFuture.failedFuture(new ActorDeliveryException(result)));
    }

    private void execute(Runnable task) {
        try {
            host.execute(id, task);
        } catch (Throwable failure) {
            fail(failure, false, true);
        }
    }

    private void processNext() {
        final Pending<M> pending;
        final S snapshot;
        synchronized (this) {
            scheduled = false;
            if (stopped || inFlight || mailbox.isEmpty()) {
                return;
            }
            inFlight = true;
            pending = mailbox.removeFirst();
            current = pending;
        }

        try {
            snapshot = initialize();
            ActorBehavior<S, M> behavior = definition.behavior();
            CompletionStage<ActorEffect<S>> stage = Objects.requireNonNull(
                    behavior.receive(new ActorContext<>(id, ref, pending.envelope()),
                            snapshot, pending.envelope().message()),
                    "behavior completion stage");
            stage.whenComplete((effect, failure) ->
                    execute(() -> complete(pending, effect, failure)));
        } catch (Throwable failure) {
            complete(pending, null, failure);
        }
    }

    private void complete(Pending<M> pending, ActorEffect<S> effect, Throwable failure) {
        if (failure != null || effect == null) {
            Throwable cause = failure != null ? failure : new NullPointerException("actor effect");
            fail(cause, false, true);
            return;
        }

        final List<Consumer<S>> observers;
        final S committed;
        final boolean stopping;
        final boolean release;
        synchronized (this) {
            if (stopped || current != pending) {
                settle(pending, new IllegalStateException("Actor stopped"));
                return;
            }
            if (effect.stateChanged()) {
                currentState = effect.state();
                observers = List.copyOf(stateObservers);
                committed = currentState;
            } else {
                observers = List.of();
                committed = null;
            }
            stopping = effect.stopsActor();
            release = effect.passivatesActor();
            if (stopping) {
                stopped = true;
            }
        }

        try {
            for (ActorEffect.Delivery<?> delivery : effect.deliveries()) {
                host.dispatch(id, delivery);
            }
        } catch (Throwable deliveryFailure) {
            fail(deliveryFailure, false, true);
            return;
        }

        for (Consumer<S> observer : observers) {
            notifyObserver(observer, committed);
        }

        if (stopping) {
            terminate(release);
            failPending(new IllegalStateException("Actor stopped"));
        }
        observe(() -> host.processed(id));
        settle(pending, null);

        final boolean enqueueRunner;
        synchronized (this) {
            if (current == pending) {
                current = null;
            }
            inFlight = false;
            enqueueRunner = !stopped && !mailbox.isEmpty();
            if (enqueueRunner) {
                scheduled = true;
            }
        }
        if (enqueueRunner) {
            execute(this::processNext);
        }
    }

    private void fail(Throwable failure, boolean release, boolean reportFailure) {
        Objects.requireNonNull(failure, "failure");
        final List<Pending<M>> rejected;
        synchronized (this) {
            if (stopped && mailbox.isEmpty() && current == null) {
                return;
            }
            stopped = true;
            rejected = new ArrayList<>(mailbox);
            mailbox.clear();
            if (current != null) {
                rejected.add(current);
                current = null;
            }
            inFlight = false;
            scheduled = false;
            stateObservers.clear();
        }
        if (reportFailure) {
            logger.log(System.Logger.Level.ERROR,
                    "Actor failed [actorType=" + id.type().name()
                            + ", failureType=" + failure.getClass().getName() + "]");
            observe(() -> host.failed(id, failure));
        }
        terminate(release);
        rejected.forEach(pending -> settle(pending, failure));
    }

    private void failPending(Throwable failure) {
        final List<Pending<M>> rejected;
        synchronized (this) {
            rejected = new ArrayList<>(mailbox);
            mailbox.clear();
        }
        rejected.forEach(pending -> settle(pending, failure));
    }

    private void terminate(boolean release) {
        synchronized (this) {
            if (terminated) {
                return;
            }
            terminated = true;
            stateObservers.clear();
        }
        host.terminated(id, this, release);
    }

    private void settle(Pending<M> pending, Throwable failure) {
        if (!pending.settled().compareAndSet(false, true)) {
            return;
        }
        if (failure == null) {
            pending.processed().complete(null);
        } else {
            pending.processed().completeExceptionally(failure);
        }
        host.settled(id);
    }

    private static void observe(Runnable callback) {
        try {
            callback.run();
        } catch (Throwable ignored) {
            // Observability must not alter actor delivery.
        }
    }

    private static <S> void notifyObserver(Consumer<S> observer, S snapshot) {
        try {
            observer.accept(snapshot);
        } catch (Throwable ignored) {
            // Rendering/inspection must not fail actor processing.
        }
    }

    private final class ActivationRef implements ActorRef<M> {
        @Override
        public SendResult tell(ActorEnvelope<M> envelope) {
            return offer(envelope, false).admission();
        }

        @Override
        public ProcessingReceipt track(ActorEnvelope<M> envelope) {
            return offer(envelope, false);
        }

        @Override
        public java.util.Optional<ActorId<M>> id() {
            return java.util.Optional.of(id);
        }
    }

    private record Pending<M>(ActorEnvelope<M> envelope,
                              CompletableFuture<Void> processed,
                              AtomicBoolean settled) {
        private Pending(ActorEnvelope<M> envelope, CompletableFuture<Void> processed) {
            this(envelope, processed, new AtomicBoolean());
        }
    }
}
