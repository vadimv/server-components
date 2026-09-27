package rsp.actor.testkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import rsp.actor.ActorBehavior;
import rsp.actor.ActorDefinition;
import rsp.actor.ActorEffect;
import rsp.actor.ActorRef;
import rsp.actor.ActorType;
import rsp.actor.SendResult;
import rsp.actor.runtime.ActorSystemObserver;
import rsp.actor.runtime.LocalActorSystem;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class InlineExecutorTests {
    private static final ActorType<String, Integer> COUNTER =
            ActorType.named("inline-counter", Integer.class, key -> key);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void processesLongSelfMessageChain(boolean asynchronousFirstTurn) {
        int length = 10_000;
        List<Integer> received = new ArrayList<>();
        ActorProbe<Integer> finalStates = new ActorProbe<>();
        CompletableFuture<ActorEffect<Integer>> firstEffect = new CompletableFuture<>();
        try (var system = builder(Runnable::run, (context, state, message) -> {
            received.add(message);
            if (asynchronousFirstTurn && message == length) {
                return firstEffect;
            }
            ActorEffect<Integer> effect = ActorEffect.state(state + 1);
            return CompletableFuture.completedFuture(message > 0
                    ? effect.send(context.self(), message - 1)
                    : effect.send(finalStates, state + 1));
        }).build()) {
            system.start();
            ActorRef<Integer> actor = system.ref(COUNTER, "self");
            var receipt = actor.track(length);
            if (asynchronousFirstTurn) {
                assertFalse(receipt.processed().toCompletableFuture().isDone());
                firstEffect.complete(ActorEffect.<Integer>state(1).send(actor, length - 1));
            }
            assertEquals(length + 1, received.size());
            for (int index = 0; index <= length; index++) {
                assertEquals(length - index, received.get(index));
            }
            assertEquals(List.of(length + 1), finalStates.messages());
            assertTrue(receipt.processed().toCompletableFuture().isDone());
            receipt.processed().toCompletableFuture().join();
            assertDrained(system);
        }
    }

    @Test
    void processesLongChainAcrossNewActorKeys() {
        int length = 2_000;
        List<Integer> received = new ArrayList<>();
        AtomicReference<LocalActorSystem> runtime = new AtomicReference<>();
        try (var system = builder(Runnable::run, ActorBehavior.sync((_, _, message) -> {
            received.add(message);
            return message == 0 ? ActorEffect.same() : ActorEffect.<Integer>same()
                    .send(runtime.get().ref(COUNTER, Integer.toString(message - 1)), message - 1);
        })).build()) {
            runtime.set(system);
            system.start();
            var receipt = system.ref(COUNTER, Integer.toString(length)).track(length);
            assertEquals(length + 1, received.size());
            for (int index = 0; index <= length; index++) {
                assertEquals(length - index, received.get(index));
            }
            assertTrue(receipt.processed().toCompletableFuture().isDone());
            receipt.processed().toCompletableFuture().join();
            assertDrained(system);
        }
    }

    @Test
    void queuedExecutorStillSchedulesEachTurnAndCompletionSeparately() {
        ManualActorExecutor executor = new ManualActorExecutor();
        ActorProbe<Integer> states = new ActorProbe<>();
        try (var system = builder(executor, ActorBehavior.sync((_, state, message) ->
                ActorEffect.<Integer>state(state + message).send(states, state + message))).build()) {
            system.start();
            var actor = system.ref(COUNTER, "queued");
            var first = actor.track(1);
            var second = actor.track(2);
            assertEquals(1, executor.pendingCount());
            assertTrue(executor.runNext());
            assertFalse(first.processed().toCompletableFuture().isDone());
            assertTrue(states.messages().isEmpty());
            assertEquals(1, executor.pendingCount());

            assertTrue(executor.runNext());
            assertTrue(first.processed().toCompletableFuture().isDone());
            assertFalse(second.processed().toCompletableFuture().isDone());
            assertEquals(List.of(1), states.messages());
            executor.runAll();
            assertEquals(List.of(1, 3), states.messages());
            second.processed().toCompletableFuture().join();
            assertDrained(system);
        }
    }

    @Test
    void executorRejectionFailsReceiptAndDoesNotRetainInlineWork() {
        AtomicInteger submissions = new AtomicInteger();
        RejectedExecutionException rejected = new RejectedExecutionException("completion rejected");
        Executor executor = task -> {
            if (submissions.incrementAndGet() == 2) {
                throw rejected;
            }
            task.run();
        };
        try (var system = builder(executor, ActorBehavior.sync((_, state, message) ->
                ActorEffect.state(state + message))).build()) {
            system.start();
            var failed = system.ref(COUNTER, "rejected").track(1);
            assertTrue(failed.processed().toCompletableFuture().isCompletedExceptionally());
            assertSame(rejected, assertThrows(CompletionException.class,
                    () -> failed.processed().toCompletableFuture().join()).getCause());
            var healthy = system.ref(COUNTER, "healthy").track(2);
            assertTrue(healthy.processed().toCompletableFuture().isDone());
            healthy.processed().toCompletableFuture().join();
            assertDrained(system);
        }
    }

    @Test
    void queuedCompletionFailureDoesNotStrandOtherActors() {
        AtomicReference<LocalActorSystem> runtime = new AtomicReference<>();
        ActorProbe<Integer> states = new ActorProbe<>();
        List<Throwable> failures = new ArrayList<>();
        AtomicInteger cancellations = new AtomicInteger();
        IllegalStateException cancelled = new IllegalStateException("cancellation failed");
        try (var system = builder(Runnable::run, ActorBehavior.sync((context, state, message) ->
                switch (context.id().key()) {
                    case "source" -> ActorEffect.<Integer>same()
                            .send(runtime.get().ref(COUNTER, "bad"), 1)
                            .send(runtime.get().ref(COUNTER, "healthy"), 7);
                    case "bad" -> message == 0 ? ActorEffect.<Integer>same()
                            .schedule(context.self(), 1, Duration.ofSeconds(1))
                            : ActorEffect.<Integer>same().stopping();
                    default -> ActorEffect.<Integer>state(state + message).send(states, state + message);
                }))
                .scheduler((_, _) -> () -> {
                    if (cancellations.getAndIncrement() == 0) {
                        throw cancelled;
                    }
                })
                .observer(new ActorSystemObserver() {
                    @Override
                    public void actorFailed(rsp.actor.ActorId<?> id, Throwable failure) {
                        failures.add(failure);
                    }
                }).build()) {
            runtime.set(system);
            system.start();
            var bad = system.ref(COUNTER, "bad");
            bad.track(0).processed().toCompletableFuture().join();
            var source = system.ref(COUNTER, "source").track(0);
            assertEquals(List.of(7), states.messages());
            assertEquals(List.of(cancelled), failures);
            assertEquals(SendResult.STOPPED, bad.tell(2));
            source.processed().toCompletableFuture().join();

            var healthy = system.ref(COUNTER, "healthy").track(1);
            assertTrue(healthy.processed().toCompletableFuture().isDone());
            healthy.processed().toCompletableFuture().join();
            assertEquals(List.of(7, 8), states.messages());
            assertDrained(system);
        }
    }

    private static LocalActorSystem.Builder builder(Executor executor,
                                                   ActorBehavior<Integer, Integer> behavior) {
        return LocalActorSystem.builder().executor(executor)
                .scheduler(new ManualActorScheduler())
                .shutdownTimeout(Duration.ofMillis(100))
                .register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                        .initialState(_ -> 0).behavior(behavior).mailboxCapacity(2).build());
    }

    private static void assertDrained(LocalActorSystem system) {
        var drained = system.drainAndStop().toCompletableFuture();
        assertTrue(drained.isDone(), "All inline work must settle before shutdown completes");
        drained.join();
    }
}
