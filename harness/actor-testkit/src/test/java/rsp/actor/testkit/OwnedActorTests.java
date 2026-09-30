package rsp.actor.testkit;

import org.junit.jupiter.api.Test;
import rsp.actor.*;
import rsp.actor.runtime.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OwnedActorTests {
    private static final ActorType<String, Integer> TYPE = ActorType.named("owned", Integer.class, key -> key);

    private static ActorDefinition<Integer, Integer> counter() {
        return ActorDefinition.<Integer, Integer>builder(TYPE).initialState(_ -> 0)
                .behavior(ActorBehavior.sync((_, state, value) -> ActorEffect.state(state + value))).build();
    }

    @Test
    void initializedViewTracksCommitsAndTerminationWithoutTransferringOwnership() throws Exception {
        var definition = counter();
        try (var system = LocalActorSystem.builder().register(definition).executor(Runnable::run).build()) {
            assertThrows(ActorDeliveryException.class, () -> system.createOwned(TYPE.id("a"), definition));
            system.start();
            var actor = system.createOwned(TYPE.id("a"), definition);
            assertEquals(0, actor.view().state());
            List<ActorSnapshot<Integer>> seen = new ArrayList<>();
            actor.view().observeSnapshots(_ -> { throw new IllegalStateException("observer"); });
            var observation = actor.view().observeSnapshots(seen::add);
            actor.view().ref().track(2).processed().toCompletableFuture().join();
            assertEquals(List.of(0L, 1L), seen.stream().map(ActorSnapshot::revision).toList());
            assertEquals(2, seen.getLast().state());
            observation.close();
            actor.view().ref().tell(3);
            assertEquals(2, seen.size());
            actor.close();
            actor.close();
            assertEquals(SendResult.STOPPED, actor.view().ref().tell(1));
            assertEquals(5, actor.view().state());
            assertEquals(ActorSnapshot.Status.STOPPED, actor.view().snapshot().status());
            actor.view().observeSnapshots(seen::add).close();
            assertEquals(3L, seen.getLast().revision());
            var replacement = system.createOwned(TYPE.id("a"), definition);
            actor.close();
            replacement.view().ref().tell(7);
            assertEquals(7, replacement.view().state());
            assertEquals(5, actor.view().state());
        }
    }

    @Test
    void rejectsDuplicatesAndMismatchedDefinitionsAndReleasesFailedInitialization() {
        AtomicInteger attempts = new AtomicInteger();
        var definition = ActorDefinition.<Integer, Integer>builder(TYPE).initialState(_ -> {
            if (attempts.getAndIncrement() == 0) throw new IllegalStateException("initialization");
            return 0;
        }).behavior(ActorBehavior.sync((_, state, _) -> ActorEffect.state(state))).build();
        try (var system = LocalActorSystem.builder().register(definition).build()) {
            system.start();
            assertThrows(IllegalArgumentException.class, () -> system.createOwned(TYPE.id("a"), counter()));
            assertThrows(IllegalStateException.class, () -> system.createOwned(TYPE.id("a"), definition));
            var actor = system.createOwned(TYPE.id("a"), definition);
            assertThrows(IllegalStateException.class, () -> system.createOwned(TYPE.id("a"), definition));
            assertSame(actor.view().ref(), system.ref(TYPE.id("a")));
        }
    }

    @Test
    void closingFailsPendingAskAndIgnoresLateAsyncCompletion() {
        var executor = new ManualActorExecutor();
        var scheduler = new ManualActorScheduler();
        var completion = new CompletableFuture<ActorEffect<Integer>>();
        var definition = ActorDefinition.<Integer, Integer>builder(TYPE).initialState(_ -> 0)
                .behavior((_, _, _) -> completion).build();
        var messages = new ActorProbe<Integer>();
        try (var system = LocalActorSystem.builder().register(definition)
                .executor(executor).scheduler(scheduler).build()) {
            system.start();
            var actor = system.createOwned(TYPE.id("a"), definition);
            var ask = system.ask(actor.view().ref(), _ -> 1, Duration.ofSeconds(1));
            executor.runAll();
            actor.close();
            assertTrue(ask.toCompletableFuture().isCompletedExceptionally());
            assertEquals(0, scheduler.pendingCount());
            completion.complete(ActorEffect.<Integer>state(99).send(messages, 1)
                    .schedule(messages, 2, Duration.ofSeconds(1)));
            executor.runAll();
            scheduler.advance(Duration.ofSeconds(2));
            assertEquals(0, actor.view().state());
            assertTrue(messages.messages().isEmpty());
        }
    }

    @Test
    void cancellationRacingWithTimerRegistrationCannotAffectReplacement() throws Exception {
        CountDownLatch registered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Runnable> callback = new AtomicReference<>();
        AtomicInteger cancellations = new AtomicInteger();
        CountDownLatch cancelled = new CountDownLatch(1);
        ActorScheduler scheduler = (_, task) -> {
            callback.set(task);
            registered.countDown();
            await(release);
            return () -> { cancellations.incrementAndGet(); cancelled.countDown(); };
        };
        var probe = new ActorProbe<Integer>();
        var definition = ActorDefinition.<Integer, Integer>builder(TYPE).initialState(_ -> 0)
                .behavior(ActorBehavior.sync((_, _, value) -> ActorEffect.<Integer>state(value)
                        .schedule(probe, value, Duration.ofSeconds(1)))).build();
        try (var system = LocalActorSystem.builder().register(definition).scheduler(scheduler).build()) {
            system.start();
            var actor = system.createOwned(TYPE.id("a"), definition);
            var receipt = actor.view().ref().track(1);
            assertTrue(registered.await(5, TimeUnit.SECONDS));
            actor.close();
            var next = system.createOwned(TYPE.id("a"), definition);
            release.countDown();
            callback.get().run(); // Even an uncooperative scheduler cannot deliver the old timer.
            assertTrue(probe.messages().isEmpty());
            assertTrue(receipt.processed().toCompletableFuture().isCompletedExceptionally());
            next.view().ref().track(2).processed().toCompletableFuture().get(5, TimeUnit.SECONDS);
            actor.close();
            callback.get().run();
            assertEquals(List.of(2), probe.messages());
            assertTrue(cancelled.await(5, TimeUnit.SECONDS));
            assertEquals(1, cancellations.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void registrationCapturesRevisionEvenWhenItsInitialCallbackArrivesLate() throws Exception {
        var definition = counter();
        try (var system = LocalActorSystem.builder().register(definition).executor(Runnable::run).build()) {
            system.start();
            var actor = system.createOwned(TYPE.id("a"), definition);
            CountDownLatch captured = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicReference<ActorSnapshot<Integer>> latest = new AtomicReference<>();
            Thread observing = Thread.ofVirtual().start(() -> actor.view().observeSnapshots(snapshot -> {
                if (snapshot.revision() == 0) {
                    captured.countDown();
                    await(release);
                }
                latest.accumulateAndGet(snapshot, (old, next) ->
                        old == null || next.revision() > old.revision() ? next : old);
            }));
            try {
                assertTrue(captured.await(5, TimeUnit.SECONDS));
                actor.view().ref().tell(42);
                assertEquals(42, latest.get().state());
            } finally {
                release.countDown();
                observing.join(5000);
            }
            assertFalse(observing.isAlive());
            assertEquals(1, latest.get().revision());
        }
    }

    @Test
    void initializationRacingWithShutdownIsAccountedForAndReleased() throws Exception {
        CountDownLatch initializing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var definition = ActorDefinition.<Integer, Integer>builder(TYPE).initialState(_ -> {
            initializing.countDown();
            await(release);
            return 0;
        }).behavior(ActorBehavior.sync((_, _, _) -> ActorEffect.same())).build();
        try (var system = LocalActorSystem.builder().register(definition).build()) {
            system.start();
            CompletableFuture<OwnedActor<Integer, Integer>> creation = CompletableFuture.supplyAsync(
                    () -> system.createOwned(TYPE.id("a"), definition));
            try {
                assertTrue(initializing.await(5, TimeUnit.SECONDS));
                var stopped = system.drainAndStop().toCompletableFuture();
                assertFalse(stopped.isDone());
                release.countDown();
                assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> creation.get(5, TimeUnit.SECONDS));
                stopped.get(5, TimeUnit.SECONDS);
                assertEquals(SendResult.STOPPED, system.ref(TYPE.id("a")).tell(1));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void failureAndGracefulShutdownPublishTerminalSnapshots() {
        var definition = ActorDefinition.<Integer, Integer>builder(TYPE).initialState(_ -> 1)
                .behavior(ActorBehavior.sync((_, _, _) -> { throw new IllegalStateException("failed"); })).build();
        var system = LocalActorSystem.builder().register(definition).executor(Runnable::run).build();
        system.start();
        var failed = system.createOwned(TYPE.id("failed"), definition);
        var idle = system.createOwned(TYPE.id("idle"), definition);
        failed.view().ref().tell(1);
        assertEquals(ActorSnapshot.Status.FAILED, failed.view().snapshot().status());
        system.stop();
        assertEquals(ActorSnapshot.Status.STOPPED, idle.view().snapshot().status());
    }

    @Test
    void normalStopReceiptSurvivesClosingTheOwnerFromItsTerminalObserver() {
        var definition = ActorDefinition.<Integer, Integer>builder(TYPE).initialState(_ -> 0)
                .behavior(ActorBehavior.sync((_, _, value) -> ActorEffect.state(value).stopping())).build();
        try (var system = LocalActorSystem.builder().register(definition).executor(Runnable::run).build()) {
            system.start();
            var owner = system.createOwned(TYPE.id("a"), definition);
            owner.view().observeSnapshots(snapshot -> {
                if (!snapshot.active()) owner.close();
            });
            var receipt = owner.view().ref().track(42);
            assertEquals(SendResult.ACCEPTED, receipt.admission());
            assertDoesNotThrow(() -> receipt.processed().toCompletableFuture().join());
            assertEquals(42, owner.view().state());
            assertEquals(ActorSnapshot.Status.STOPPED, owner.view().snapshot().status());
            system.createOwned(TYPE.id("a"), definition).close();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Latch timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
