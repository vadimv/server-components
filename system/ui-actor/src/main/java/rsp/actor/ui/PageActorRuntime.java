package rsp.actor.ui;

import rsp.actor.ActorAskTimeoutException;
import rsp.actor.ActorDefinition;
import rsp.actor.ActorDeliveryException;
import rsp.actor.ActorEffect;
import rsp.actor.ActorEnvelope;
import rsp.actor.ActorGateway;
import rsp.actor.ActorId;
import rsp.actor.ActorRef;
import rsp.actor.ProcessingReceipt;
import rsp.actor.SendResult;
import rsp.actor.runtime.ActorScheduler;
import rsp.actor.runtime.ActorSystemObserver;
import rsp.actor.runtime.SerializedActorActivation;
import rsp.application.ApplicationLifecycle;
import rsp.component.CommandsEnqueue;
import rsp.page.PageScope;
import rsp.page.events.GenericTaskEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Application-owned runtime for actors whose turns execute on their page's
 * event loop and whose activations are owned by {@link PageScope}.
 */
public final class PageActorRuntime implements ActorGateway, ApplicationLifecycle {
    private enum State { NEW, RUNNING, STOPPED }

    private final Map<ActorId<?>, SerializedActorActivation<?, ?>> activations =
            new ConcurrentHashMap<>();
    private final Set<CompletableFuture<?>> asks = ConcurrentHashMap.newKeySet();
    private final List<TimerSlot> timers = new ArrayList<>();
    private final ActorScheduler scheduler;
    private final ScheduledExecutorService ownedScheduler;
    private final ActorSystemObserver observer;
    private volatile State state = State.NEW;

    private PageActorRuntime(Builder builder) {
        ownedScheduler = builder.scheduler == null ? newScheduler() : null;
        scheduler = builder.scheduler == null
                ? (delay, task) -> {
                    var future = ownedScheduler.schedule(task, delay.toNanos(), TimeUnit.NANOSECONDS);
                    return () -> future.cancel(false);
                }
                : builder.scheduler;
        observer = builder.observer;
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
        if (state == State.STOPPED) {
            throw new IllegalStateException("Page actor runtime cannot be restarted");
        }
        state = State.RUNNING;
        observe(observer::systemStarted);
    }

    @Override
    public void stop() {
        final List<SerializedActorActivation<?, ?>> closing;
        synchronized (this) {
            if (state == State.STOPPED) {
                return;
            }
            state = State.STOPPED;
            observe(observer::systemStopping);
            closing = List.copyOf(activations.values());
        }
        closing.forEach(activation -> activation.close(
                new ActorDeliveryException(SendResult.STOPPED)));
        synchronized (timers) {
            timers.forEach(TimerSlot::cancel);
            timers.clear();
        }
        asks.forEach(ask -> ask.completeExceptionally(
                new ActorDeliveryException(SendResult.STOPPED)));
        if (ownedScheduler != null) {
            ownedScheduler.shutdownNow();
        }
        observe(() -> observer.systemStopped(null));
    }

    synchronized <S, M> PageActorHandle<S, M> activate(
            ActorId<M> id,
            ActorDefinition<S, M> definition,
            CommandsEnqueue commands,
            PageScope scope,
            Runnable onTerminated) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(onTerminated, "onTerminated");
        if (state != State.RUNNING) {
            throw new IllegalStateException("Page actor runtime is not running");
        }

        PageHost host = new PageHost(commands, onTerminated);
        SerializedActorActivation<S, M> activation =
                new SerializedActorActivation<>(id, definition, host);
        if (activations.putIfAbsent(id, activation) != null) {
            throw new IllegalStateException("Actor ID is already active: " + id);
        }
        PageActorHandle<S, M> handle = new PageActorHandle<>(activation);
        try {
            activation.initialize();
            scope.own(handle);
            return handle;
        } catch (RuntimeException | Error failure) {
            activations.remove(id, activation);
            activation.close(failure);
            throw failure;
        }
    }

    @Override
    public <M, R> CompletionStage<R> ask(ActorRef<M> target,
                                          Function<ActorRef<R>, M> command,
                                          Duration timeout) {
        Objects.requireNonNull(command, "command");
        return askEnvelope(target, replyTo -> ActorEnvelope.of(command.apply(replyTo)), timeout);
    }

    @Override
    public <M, R> CompletionStage<R> askEnvelope(
            ActorRef<M> target,
            Function<ActorRef<R>, ActorEnvelope<M>> command,
            Duration timeout) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(command, "command");
        requirePositive(timeout, "timeout");
        if (state != State.RUNNING) {
            return CompletableFuture.failedFuture(
                    new ActorDeliveryException(state == State.NEW
                            ? SendResult.NOT_STARTED : SendResult.STOPPED));
        }

        CompletableFuture<R> result = new CompletableFuture<>();
        asks.add(result);
        result.whenComplete((_, _) -> asks.remove(result));
        ActorRef<R> replyTo = new ActorRef<>() {
            @Override
            public SendResult tell(ActorEnvelope<R> envelope) {
                Objects.requireNonNull(envelope, "envelope");
                return result.complete(envelope.message())
                        ? SendResult.ACCEPTED : SendResult.STOPPED;
            }

            @Override
            public ProcessingReceipt track(ActorEnvelope<R> envelope) {
                SendResult admission = tell(envelope);
                return receipt(admission);
            }
        };

        try {
            ActorEnvelope<M> envelope = Objects.requireNonNull(
                    command.apply(replyTo), "ask envelope");
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

    private static ProcessingReceipt receipt(SendResult result) {
        return result == SendResult.ACCEPTED
                ? new ProcessingReceipt(result, CompletableFuture.completedFuture(null))
                : new ProcessingReceipt(result,
                CompletableFuture.failedFuture(new ActorDeliveryException(result)));
    }

    private void dispatch(ActorId<?> sender, ActorEffect.Delivery<?> delivery) {
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
        SerializedActorActivation<?, ?> raw = recipient.id()
                .map(activations::get).orElse(null);
        SendResult result;
        if (internal && raw != null && raw.ref() == recipient) {
            @SuppressWarnings("unchecked")
            SerializedActorActivation<?, M> local = (SerializedActorActivation<?, M>) raw;
            result = local.offerInternal(delivery.envelope()).admission();
        } else {
            result = recipient.tell(delivery.envelope());
        }
        if (result != SendResult.ACCEPTED) {
            observe(() -> observer.outboundRejected(sender, recipient, result));
        }
    }

    private void cancelTimers(ActorId<?> id) {
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

    private static void observe(Runnable callback) {
        try {
            callback.run();
        } catch (Throwable ignored) {
            // Observability must not alter delivery.
        }
    }

    public static final class Builder {
        private ActorScheduler scheduler;
        private ActorSystemObserver observer = ActorSystemObserver.NOOP;

        public Builder scheduler(ActorScheduler value) {
            scheduler = Objects.requireNonNull(value, "scheduler");
            return this;
        }

        public Builder observer(ActorSystemObserver value) {
            observer = Objects.requireNonNull(value, "observer");
            return this;
        }

        public PageActorRuntime build() {
            return new PageActorRuntime(this);
        }
    }

    private final class PageHost implements SerializedActorActivation.Host {
        private final CommandsEnqueue commands;
        private final Runnable onTerminated;

        private PageHost(CommandsEnqueue commands, Runnable onTerminated) {
            this.commands = commands;
            this.onTerminated = onTerminated;
        }

        @Override
        public SendResult admission(ActorId<?> id, boolean internal) {
            return state == State.RUNNING ? SendResult.ACCEPTED
                    : state == State.NEW ? SendResult.NOT_STARTED : SendResult.STOPPED;
        }

        @Override
        public void execute(ActorId<?> id, Runnable task) {
            commands.offer(new GenericTaskEvent(task));
        }

        @Override
        public void settled(ActorId<?> id) {
        }

        @Override
        public void dispatch(ActorId<?> sender, ActorEffect.Delivery<?> delivery) {
            PageActorRuntime.this.dispatch(sender, delivery);
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
            cancelTimers(id);
            activations.remove(id, activation);
            observe(onTerminated);
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
            if (cancellation != null) {
                cancellation.cancel();
            }
        }
    }
}
