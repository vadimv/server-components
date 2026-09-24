package rsp.actor.runtime;

import rsp.actor.ActorAskTimeoutException;
import rsp.actor.ActorDefinition;
import rsp.actor.ActorDeliveryException;
import rsp.actor.ActorEffect;
import rsp.actor.ActorEnvelope;
import rsp.actor.ActorId;
import rsp.actor.ActorRef;
import rsp.actor.ActorSystem;
import rsp.actor.ProcessingReceipt;
import rsp.actor.SendResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** In-JVM, at-most-once actor runtime with bounded keyed mailboxes. */
public final class LocalActorSystem implements ActorSystem {
    private static final System.Logger logger = System.getLogger(LocalActorSystem.class.getName());

    private enum State { NEW, RUNNING, STOPPING, STOPPED }

    private final Map<String, ActorDefinition<?, ?>> definitions;
    private final Map<ActorId<?>, SerializedActorActivation<?, ?>> cells = new ConcurrentHashMap<>();
    private final Executor executor;
    private final ActorScheduler scheduler;
    private final ExecutorService ownedExecutor;
    private final ScheduledExecutorService ownedScheduler;
    private final ActorSystemObserver observer;
    private final Duration shutdownTimeout;
    private final List<TimerSlot> timers = new ArrayList<>();
    private final java.util.Set<CompletableFuture<?>> asks = ConcurrentHashMap.newKeySet();
    // Guards admission, outstanding work, and lifecycle transitions. Callbacks,
    // timer cancellation, and activation access must run outside this lock.
    private final Object lifecycleLock = new Object();
    private int outstanding;
    private final CompletableFuture<Void> drained = new CompletableFuture<>();
    private final SerializedActorActivation.Host activationHost = new ActivationHost();
    private volatile State state = State.NEW;

    private LocalActorSystem(Builder builder) {
        definitions = Map.copyOf(builder.definitions);
        ownedExecutor = builder.executor == null ? Executors.newVirtualThreadPerTaskExecutor() : null;
        executor = builder.executor == null ? ownedExecutor : builder.executor;
        ownedScheduler = builder.scheduler == null ? newScheduler() : null;
        scheduler = builder.scheduler == null
                ? (delay, task) -> {
                    var future = ownedScheduler.schedule(task, delay.toNanos(), TimeUnit.NANOSECONDS);
                    return () -> future.cancel(false);
                }
                : builder.scheduler;
        observer = builder.observer;
        shutdownTimeout = builder.shutdownTimeout;
        drained.whenComplete((_, failure) -> {
            asks.forEach(ask -> ask.completeExceptionally(new ActorDeliveryException(SendResult.STOPPED)));
            closeOwnedResources();
            observe(() -> observer.systemStopped(failure));
        });
    }

    public static Builder builder() {
        return new Builder();
    }

    private static ScheduledExecutorService newScheduler() {
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(1, Thread.ofVirtual().factory());
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    @Override
    public synchronized void start() {
        synchronized (lifecycleLock) {
            if (state == State.RUNNING) {
                return;
            }
            if (state != State.NEW) {
                throw new IllegalStateException("Actor system cannot be restarted");
            }
            state = State.RUNNING;
        }
        observe(observer::systemStarted);
    }

    @Override
    public <M> ActorRef<M> ref(ActorId<M> id) {
        Objects.requireNonNull(id, "id");
        ActorDefinition<?, ?> definition = definitions.get(id.type().name());
        // Keep lookup available while draining; delivery admission rejects external sends.
        if (definition == null || !definition.type().equals(id.type())
                || state == State.STOPPED) {
            return new UnavailableRef<>(id);
        }
        @SuppressWarnings("unchecked")
        SerializedActorActivation<?, M> cell = (SerializedActorActivation<?, M>) cells.computeIfAbsent(id,
                ignored -> newCell(id, definition));
        return cell.ref();
    }

    @Override
    public <M, R> CompletionStage<R> ask(ActorRef<M> target,
                                          Function<ActorRef<R>, M> command,
                                          Duration timeout) {
        Objects.requireNonNull(command, "command");
        return askEnvelope(target, replyTo -> ActorEnvelope.of(command.apply(replyTo)), timeout);
    }

    @Override
    public <M, R> CompletionStage<R> askEnvelope(ActorRef<M> target,
                                                 Function<ActorRef<R>, ActorEnvelope<M>> command,
                                                 Duration timeout) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(command, "command");
        requirePositive(timeout, "timeout");
        if (state != State.RUNNING) {
            return CompletableFuture.failedFuture(new ActorDeliveryException(
                    state == State.NEW ? SendResult.NOT_STARTED : SendResult.STOPPED));
        }
        CompletableFuture<R> result = new CompletableFuture<>();
        asks.add(result);
        result.whenComplete((_, _) -> asks.remove(result));
        ActorRef<R> replyTo = new ActorRef<>() {
            @Override
            public SendResult tell(ActorEnvelope<R> envelope) {
                Objects.requireNonNull(envelope, "envelope");
                return result.complete(envelope.message()) ? SendResult.ACCEPTED : SendResult.STOPPED;
            }

            @Override
            public ProcessingReceipt track(ActorEnvelope<R> envelope) {
                SendResult admission = tell(envelope);
                return receipt(admission);
            }
        };
        try {
            ActorEnvelope<M> envelope = Objects.requireNonNull(command.apply(replyTo), "ask envelope");
            ProcessingReceipt receipt = target.track(envelope);
            if (receipt.admission() != SendResult.ACCEPTED) {
                result.completeExceptionally(new ActorDeliveryException(receipt.admission()));
            } else if (!result.isDone()) {
                receipt.processed().whenComplete((_, failure) -> {
                    if (failure != null) {
                        result.completeExceptionally(failure);
                    }
                });
                ActorScheduler.Cancellation cancellation = scheduler.schedule(timeout,
                        () -> result.completeExceptionally(new ActorAskTimeoutException()));
                result.whenComplete((_, _) -> cancellation.cancel());
            }
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
        }
        return result;
    }

    @Override
    public synchronized CompletionStage<Void> drainAndStop() {
        final boolean neverStarted;
        synchronized (lifecycleLock) {
            if (state == State.STOPPED || state == State.STOPPING) {
                return drained;
            }
            neverStarted = state == State.NEW;
            state = neverStarted ? State.STOPPED : State.STOPPING;
        }
        if (neverStarted) {
            drained.complete(null);
            return drained;
        }
        observe(observer::systemStopping);
        synchronized (timers) {
            timers.forEach(TimerSlot::cancel);
            timers.clear();
        }
        finishDraining();
        return drained;
    }

    @Override
    public void stop() {
        CompletableFuture<Void> completion = drainAndStop().toCompletableFuture();
        try {
            completion.get(shutdownTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            forceStop(interrupted);
        } catch (java.util.concurrent.TimeoutException timeout) {
            forceStop(timeout);
        } catch (java.util.concurrent.ExecutionException failure) {
            throw new IllegalStateException("Actor system shutdown failed", failure.getCause());
        }
    }

    private void forceStop(Throwable cause) {
        synchronized (lifecycleLock) {
            state = State.STOPPED;
        }
        cells.values().forEach(cell -> cell.close(cause));
        drained.completeExceptionally(cause);
    }

    private void finishDraining() {
        synchronized (lifecycleLock) {
            if (state != State.STOPPING || outstanding != 0) {
                return;
            }
            state = State.STOPPED;
        }
        drained.complete(null);
    }

    private void closeOwnedResources() {
        if (ownedScheduler != null) {
            ownedScheduler.shutdownNow();
        }
        if (ownedExecutor != null) {
            ownedExecutor.shutdown();
        }
    }

    private <M> SerializedActorActivation<?, M> newCell(ActorId<M> id, ActorDefinition<?, ?> raw) {
        @SuppressWarnings("unchecked")
        ActorDefinition<Object, M> definition = (ActorDefinition<Object, M>) raw;
        return new SerializedActorActivation<>(id, definition, activationHost);
    }

    private ProcessingReceipt rejected(ActorId<?> id, SendResult result) {
        observe(() -> observer.messageRejected(id, result));
        return receipt(result);
    }

    private static ProcessingReceipt receipt(SendResult result) {
        return result == SendResult.ACCEPTED
                ? new ProcessingReceipt(result, CompletableFuture.completedFuture(null))
                : new ProcessingReceipt(result,
                        CompletableFuture.failedFuture(new ActorDeliveryException(result)));
    }

    private void observe(Runnable callback) {
        try {
            callback.run();
        } catch (Throwable ignored) {
            // Observability must not alter actor delivery.
        }
    }

    private <M> void dispatch(ActorId<?> sender, ActorEffect.Delivery<M> delivery) {
        if (delivery.delay().isZero()) {
            sendEffect(sender, delivery, true);
            return;
        }
        synchronized (timers) {
            if (state != State.RUNNING) {
                return;
            }
            TimerSlot slot = new TimerSlot(sender);
            timers.add(slot);
            try {
                slot.cancellation = scheduler.schedule(delivery.delay(), () -> {
                    synchronized (timers) {
                        timers.remove(slot);
                    }
                    if (state == State.RUNNING) {
                        sendEffect(sender, delivery, false);
                    }
                });
            } catch (Throwable failure) {
                timers.remove(slot);
                throw failure;
            }
        }
    }

    private <M> void sendEffect(ActorId<?> sender, ActorEffect.Delivery<M> delivery,
                                boolean internal) {
        ActorRef<M> recipient = delivery.recipient();
        SendResult result;
        SerializedActorActivation<?, ?> raw = recipient.id()
                .map(cells::get).orElse(null);
        if (internal && raw != null && raw.ref() == recipient) {
            @SuppressWarnings("unchecked")
            SerializedActorActivation<?, M> local = (SerializedActorActivation<?, M>) raw;
            result = local.offerInternal(delivery.envelope()).admission();
        } else {
            result = recipient.tell(delivery.envelope());
        }
        if (result != SendResult.ACCEPTED) {
            logger.log(System.Logger.Level.WARNING,
                    "Actor outbound message rejected [sourceType=" + sender.type().name()
                            + ", reason=" + result + "]");
            observe(() -> observer.outboundRejected(sender, recipient, result));
        }
    }

    private static final class TimerSlot implements ActorScheduler.Cancellation {
        private final ActorId<?> owner;
        private ActorScheduler.Cancellation cancellation;

        private TimerSlot(ActorId<?> owner) {
            this.owner = owner;
        }

        @Override
        public void cancel() {
            cancellation.cancel();
        }
    }

    private void cancelActorTimers(ActorId<?> id) {
        synchronized (timers) {
            timers.removeIf(timer -> {
                if (!timer.owner.equals(id)) {
                    return false;
                }
                timer.cancel();
                return true;
            });
        }
    }

    private static void requirePositive(Duration duration, String name) {
        if (Objects.requireNonNull(duration, name).isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    public static final class Builder {
        private final Map<String, ActorDefinition<?, ?>> definitions = new java.util.LinkedHashMap<>();
        private Executor executor;
        private ActorScheduler scheduler;
        private ActorSystemObserver observer = ActorSystemObserver.NOOP;
        private Duration shutdownTimeout = Duration.ofSeconds(30);

        public Builder register(ActorDefinition<?, ?> definition) {
            Objects.requireNonNull(definition, "definition");
            if (definitions.putIfAbsent(definition.type().name(), definition) != null) {
                throw new IllegalArgumentException("Duplicate actor type: " + definition.type().name());
            }
            return this;
        }

        public Builder executor(Executor value) {
            executor = Objects.requireNonNull(value, "executor");
            return this;
        }

        public Builder scheduler(ActorScheduler value) {
            scheduler = Objects.requireNonNull(value, "scheduler");
            return this;
        }

        public Builder observer(ActorSystemObserver value) {
            observer = Objects.requireNonNull(value, "observer");
            return this;
        }

        public Builder shutdownTimeout(Duration value) {
            requirePositive(value, "shutdownTimeout");
            shutdownTimeout = value;
            return this;
        }

        public LocalActorSystem build() {
            return new LocalActorSystem(this);
        }
    }

    private final class UnavailableRef<M> implements ActorRef<M> {
        private final ActorId<M> id;

        private UnavailableRef(ActorId<M> id) {
            this.id = id;
        }

        @Override
        public SendResult tell(ActorEnvelope<M> envelope) {
            return track(envelope).admission();
        }

        @Override
        public ProcessingReceipt track(ActorEnvelope<M> envelope) {
            Objects.requireNonNull(envelope, "envelope");
            if (!id.type().messageClass().isInstance(envelope.message())) {
                throw new IllegalArgumentException(
                        "Message is not a " + id.type().messageClass().getName());
            }
            State current = state;
            SendResult result = current == State.NEW ? SendResult.UNKNOWN_ACTOR
                    : current == State.RUNNING ? SendResult.UNKNOWN_ACTOR : SendResult.STOPPED;
            return rejected(id, result);
        }

        @Override
        public java.util.Optional<ActorId<M>> id() {
            return java.util.Optional.of(id);
        }
    }

    private final class ActivationHost implements SerializedActorActivation.Host {
        @Override
        public SendResult admission(ActorId<?> id, boolean internal) {
            State current = state;
            if (current != State.RUNNING && !(internal && current == State.STOPPING)) {
                return current == State.NEW ? SendResult.NOT_STARTED : SendResult.STOPPED;
            }
            ActorDefinition<?, ?> definition = definitions.get(id.type().name());
            return definition != null && definition.type().equals(id.type())
                    ? SendResult.ACCEPTED : SendResult.UNKNOWN_ACTOR;
        }

        @Override
        public void execute(ActorId<?> id, Runnable task) {
            executor.execute(task);
        }

        @Override
        public SendResult admit(ActorId<?> id, boolean internal) {
            synchronized (lifecycleLock) {
                SendResult result = admission(id, internal);
                if (result == SendResult.ACCEPTED) {
                    outstanding++;
                }
                return result;
            }
        }

        @Override
        public void settled(ActorId<?> id) {
            synchronized (lifecycleLock) {
                outstanding--;
            }
            finishDraining();
        }

        @Override
        public void dispatch(ActorId<?> sender, ActorEffect.Delivery<?> delivery) {
            LocalActorSystem.this.dispatch(sender, delivery);
        }

        @Override
        public void activated(ActorId<?> id) {
            observe(() -> observer.actorActivated(id));
        }

        @Override
        public void rejected(ActorId<?> id, SendResult reason) {
            observe(() -> observer.messageRejected(id, reason));
        }

        @Override
        public void processed(ActorId<?> id) {
            observe(() -> observer.messageProcessed(id));
        }

        @Override
        public void failed(ActorId<?> id, Throwable failure) {
            observe(() -> observer.actorFailed(id, failure));
        }

        @Override
        public void terminated(ActorId<?> id,
                               SerializedActorActivation<?, ?> activation,
                               boolean release) {
            cancelActorTimers(id);
            if (release) {
                cells.remove(id, activation);
            }
        }
    }
}
