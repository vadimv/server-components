package rsp.actor.runtime;

import rsp.actor.ActorBehavior;
import rsp.actor.ActorAskTimeoutException;
import rsp.actor.ActorContext;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** In-JVM, at-most-once actor runtime with bounded keyed mailboxes. */
public final class LocalActorSystem implements ActorSystem {
    private static final System.Logger logger = System.getLogger(LocalActorSystem.class.getName());

    private enum State { NEW, RUNNING, STOPPING, STOPPED }

    private final Map<String, ActorDefinition<?, ?>> definitions;
    private final Map<ActorId<?>, Cell<?, ?>> cells = new ConcurrentHashMap<>();
    private final Executor executor;
    private final ActorScheduler scheduler;
    private final ExecutorService ownedExecutor;
    private final ScheduledExecutorService ownedScheduler;
    private final ActorSystemObserver observer;
    private final Duration shutdownTimeout;
    private final List<TimerSlot> timers = new ArrayList<>();
    private final java.util.Set<CompletableFuture<?>> asks = ConcurrentHashMap.newKeySet();
    private final AtomicInteger outstanding = new AtomicInteger();
    private final CompletableFuture<Void> drained = new CompletableFuture<>();
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
        if (state == State.RUNNING) {
            return;
        }
        if (state != State.NEW) {
            throw new IllegalStateException("Actor system cannot be restarted");
        }
        state = State.RUNNING;
        observe(observer::systemStarted);
    }

    @Override
    public <M> ActorRef<M> ref(ActorId<M> id) {
        Objects.requireNonNull(id, "id");
        ActorDefinition<?, ?> definition = definitions.get(id.type().name());
        if (definition == null || !definition.type().equals(id.type())
                || state == State.STOPPING || state == State.STOPPED) {
            return new LocalRef<>(id, null);
        }
        @SuppressWarnings("unchecked")
        Cell<?, M> cell = (Cell<?, M>) cells.computeIfAbsent(id,
                ignored -> newCell(id, definition));
        return new LocalRef<>(id, cell);
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
        if (state == State.STOPPED || state == State.STOPPING) {
            return drained;
        }
        if (state == State.NEW) {
            state = State.STOPPED;
            drained.complete(null);
            return drained;
        }
        state = State.STOPPING;
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
        state = State.STOPPED;
        cells.values().forEach(cell -> cell.fail(cause));
        drained.completeExceptionally(cause);
    }

    private void finishDraining() {
        if (state == State.STOPPING && outstanding.get() == 0) {
            state = State.STOPPED;
            drained.complete(null);
        }
    }

    private void closeOwnedResources() {
        if (ownedScheduler != null) {
            ownedScheduler.shutdownNow();
        }
        if (ownedExecutor != null) {
            ownedExecutor.shutdown();
        }
    }

    private <M> ProcessingReceipt offer(ActorId<M> id, Cell<?, M> cell,
                                        ActorEnvelope<M> envelope, boolean internal) {
        Objects.requireNonNull(envelope, "envelope");
        if (!id.type().messageClass().isInstance(envelope.message())) {
            throw new IllegalArgumentException("Message is not a " + id.type().messageClass().getName());
        }
        State current = state;
        if (current != State.RUNNING && !(internal && current == State.STOPPING)) {
            return rejected(id, current == State.NEW ? SendResult.NOT_STARTED : SendResult.STOPPED);
        }
        ActorDefinition<?, ?> definition = definitions.get(id.type().name());
        if (definition == null || !definition.type().equals(id.type())) {
            return rejected(id, SendResult.UNKNOWN_ACTOR);
        }
        if (cell == null) {
            return rejected(id, SendResult.UNKNOWN_ACTOR);
        }
        return cell.offer(envelope, internal);
    }

    private <M> Cell<?, M> newCell(ActorId<M> id, ActorDefinition<?, ?> raw) {
        @SuppressWarnings("unchecked")
        ActorDefinition<Object, M> definition = (ActorDefinition<Object, M>) raw;
        return new Cell<>(id, definition);
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
        if (recipient instanceof LocalActorSystem.LocalRef<?> raw && raw.owner() == this) {
            @SuppressWarnings("unchecked")
            LocalRef<M> local = (LocalRef<M>) raw;
            result = local.offer(delivery.envelope(), internal).admission();
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

    private final class LocalRef<M> implements ActorRef<M> {
        private final ActorId<M> id;
        private final Cell<?, M> cell;

        private LocalRef(ActorId<M> id, Cell<?, M> cell) {
            this.id = id;
            this.cell = cell;
        }

        private LocalActorSystem owner() {
            return LocalActorSystem.this;
        }

        @Override
        public SendResult tell(ActorEnvelope<M> envelope) {
            return offer(envelope, false).admission();
        }

        @Override
        public ProcessingReceipt track(ActorEnvelope<M> envelope) {
            return offer(envelope, false);
        }

        private ProcessingReceipt offer(ActorEnvelope<M> envelope, boolean internal) {
            return LocalActorSystem.this.offer(id, cell, envelope, internal);
        }

        @Override
        public java.util.Optional<ActorId<M>> id() {
            return java.util.Optional.of(id);
        }
    }

    private final class Cell<S, M> {
        private final ActorId<M> id;
        private final ActorDefinition<S, M> definition;
        private final Deque<Pending<M>> mailbox = new ArrayDeque<>();
        private S currentState;
        private boolean initialized;
        private boolean inFlight;
        private boolean scheduled;
        private boolean stopped;
        private Pending<M> current;

        private Cell(ActorId<M> id, ActorDefinition<S, M> definition) {
            this.id = id;
            this.definition = definition;
        }

        private ProcessingReceipt offer(ActorEnvelope<M> envelope, boolean internal) {
            CompletableFuture<Void> processed = new CompletableFuture<>();
            boolean enqueueRunner = false;
            synchronized (this) {
                State current = state;
                if (current != State.RUNNING && !(internal && current == State.STOPPING)) {
                    return rejected(id, SendResult.STOPPED);
                }
                if (stopped) {
                    return rejected(id, SendResult.STOPPED);
                }
                if (mailbox.size() >= definition.mailboxCapacity()) {
                    return rejected(id, SendResult.MAILBOX_FULL);
                }
                outstanding.incrementAndGet();
                mailbox.addLast(new Pending<>(envelope, processed));
                if (!inFlight && !scheduled) {
                    scheduled = true;
                    enqueueRunner = true;
                }
            }
            if (enqueueRunner) {
                executeNext();
            }
            return new ProcessingReceipt(SendResult.ACCEPTED, processed);
        }

        private void executeNext() {
            try {
                executor.execute(this::processNext);
            } catch (Throwable failure) {
                fail(failure);
            }
        }

        private void processNext() {
            Pending<M> pending;
            S snapshot;
            boolean initialize;
            synchronized (this) {
                scheduled = false;
                if (stopped || inFlight || mailbox.isEmpty()) {
                    return;
                }
                inFlight = true;
                pending = mailbox.removeFirst();
                current = pending;
                snapshot = currentState;
                initialize = !initialized;
            }
            try {
                if (initialize) {
                    snapshot = definition.initialState(id);
                    synchronized (this) {
                        if (stopped) {
                            return;
                        }
                        currentState = snapshot;
                        initialized = true;
                    }
                    observe(() -> observer.actorActivated(id));
                }
                ActorBehavior<S, M> behavior = definition.behavior();
                CompletionStage<ActorEffect<S>> stage = Objects.requireNonNull(behavior.receive(
                        new ActorContext<>(id, new LocalRef<>(id, this), pending.envelope()), snapshot,
                        pending.envelope().message()), "behavior completion stage");
                stage.whenComplete((effect, failure) -> complete(pending, effect, failure));
            } catch (Throwable failure) {
                complete(pending, null, failure);
            }
        }

        private void complete(Pending<M> pending, ActorEffect<S> effect, Throwable failure) {
            if (failure != null || effect == null) {
                Throwable cause = failure != null ? failure : new NullPointerException("actor effect");
                fail(cause);
                return;
            }
            synchronized (this) {
                if (stopped) {
                    settle(pending, new IllegalStateException("Actor stopped"));
                    return;
                }
                if (effect.stateChanged()) {
                    currentState = effect.state();
                }
                if (effect.stopsActor()) {
                    stopped = true;
                }
            }
            try {
                for (ActorEffect.Delivery<?> delivery : effect.deliveries()) {
                    dispatch(id, delivery);
                }
            } catch (Throwable deliveryFailure) {
                fail(deliveryFailure);
                return;
            }
            if (stopped) {
                cancelActorTimers(id);
                failPending(new IllegalStateException("Actor stopped"));
                if (effect.passivatesActor()) {
                    cells.remove(id, this);
                }
            }
            observe(() -> observer.messageProcessed(id));
            settle(pending, null);
            boolean enqueueRunner;
            synchronized (this) {
                current = null;
                inFlight = false;
                enqueueRunner = !stopped && !mailbox.isEmpty();
                if (enqueueRunner) {
                    scheduled = true;
                }
            }
            if (enqueueRunner) {
                executeNext();
            }
        }

        private void fail(Throwable failure) {
            List<Pending<M>> rejected;
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
            }
            logger.log(System.Logger.Level.ERROR,
                    "Actor failed [actorType=" + id.type().name()
                            + ", failureType=" + failure.getClass().getName() + "]");
            observe(() -> observer.actorFailed(id, failure));
            cancelActorTimers(id);
            rejected.forEach(pending -> settle(pending, failure));
        }

        private void failPending(Throwable failure) {
            List<Pending<M>> rejected;
            synchronized (this) {
                rejected = new ArrayList<>(mailbox);
                mailbox.clear();
            }
            for (Pending<M> pending : rejected) {
                settle(pending, failure);
            }
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
            outstanding.decrementAndGet();
            finishDraining();
        }
    }

    private record Pending<M>(ActorEnvelope<M> envelope, CompletableFuture<Void> processed,
                              AtomicBoolean settled) {
        private Pending(ActorEnvelope<M> envelope, CompletableFuture<Void> processed) {
            this(envelope, processed, new AtomicBoolean());
        }
    }
}
