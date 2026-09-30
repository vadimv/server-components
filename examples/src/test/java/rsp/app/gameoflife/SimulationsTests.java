package rsp.app.gameoflife;

import org.junit.jupiter.api.Test;
import rsp.actor.*;
import rsp.actor.runtime.ActorSnapshot;
import rsp.actor.runtime.LocalActorSystem;
import rsp.actor.testkit.ActorTestKit;
import rsp.actor.testkit.ManualActorScheduler;
import rsp.actor.runtime.ActorScheduler;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SimulationsTests {
    @Test
    void javaFacadeCreatesAndAdvancesWithoutAnyPageAndControlsTheSameActor() {
        var definition = LifeGame.definition(new Random(42));
        var kit = new ActorTestKit();
        try (var actors = kit.register(definition).start();
             var simulations = service(actors, definition, 2, kit.scheduler())) {
            simulations.start();
            var creation = simulations.create().toCompletableFuture();
            assertFalse(creation.isDone());
            assertTrue(simulations.list().isEmpty());
            kit.runAll();
            var first = creation.join();
            assertEquals(LifeGame.Phase.RUNNING, first.snapshot().state().summary().status());
            kit.advance(LifeGame.TICK_INTERVAL);
            assertEquals(1, simulations.snapshot(first.id()).state().summary().generation());
            var pause = simulations.control(first.id(), LifeGame.Action.PAUSE).toCompletableFuture();
            kit.runAll();
            assertEquals(LifeGame.Phase.PAUSED, pause.join().status());
            long revision = first.snapshot().revision();
            kit.advance(LifeGame.TICK_INTERVAL.multipliedBy(2));
            assertEquals(revision, first.snapshot().revision());
            assertSame(first.view(), simulations.find(first.id()).orElseThrow().view());
        }
    }

    @Test
    void capacityIncludesPendingCreationsAndDeletionReleasesIt() {
        var definition = LifeGame.definition(new Random(42));
        var kit = new ActorTestKit();
        try (var actors = kit.register(definition).start();
             var simulations = service(actors, definition, 1, kit.scheduler())) {
            simulations.start();
            var first = simulations.create().toCompletableFuture();
            var full = assertThrows(CompletionException.class, () -> simulations.create().toCompletableFuture().join());
            assertInstanceOf(Simulations.CapacityExceeded.class, full.getCause());
            kit.runAll();
            long id = first.join().id();
            simulations.delete(id);
            assertEquals(ActorSnapshot.Status.STOPPED, first.join().snapshot().status());
            assertTrue(simulations.find(id).isEmpty());
            assertEquals(0, kit.scheduler().pendingCount());
            assertThrows(Simulations.MissingSimulation.class, () -> simulations.delete(id));
            var next = simulations.create().toCompletableFuture();
            kit.runAll();
            assertNotEquals(id, next.join().id());
            assertEquals(1, simulations.list().size());
        }
    }

    @Test
    void creationTimeoutClosesTheActorAndReleasesReservedCapacity() {
        var definition = ActorDefinition.<LifeGame.State, LifeGame.Command>builder(LifeGame.TYPE)
                .initialState(id -> initial(Long.parseLong(id.key())))
                .behavior((_, _, _) -> new CompletableFuture<>()).build();
        var kit = new ActorTestKit();
        try (var actors = kit.register(definition).start();
             var simulations = service(actors, definition, 1, kit.scheduler())) {
            simulations.start();
            var creation = simulations.create().toCompletableFuture();
            kit.runAll();
            kit.advance(Duration.ofSeconds(2));
            assertInstanceOf(ActorAskTimeoutException.class,
                    assertThrows(CompletionException.class, creation::join).getCause());
            assertTrue(simulations.list().isEmpty());
            assertEquals(0, kit.scheduler().pendingCount());
            var next = simulations.create().toCompletableFuture();
            assertFalse(next.isDone()); // Capacity was released.
            simulations.stop();
            assertTrue(next.isCompletedExceptionally());
            kit.runAll();
        }
    }

    @Test
    void failedInitializationDoesNotOccupyCapacity() {
        var definition = ActorDefinition.<LifeGame.State, LifeGame.Command>builder(LifeGame.TYPE)
                .initialState(_ -> { throw new IllegalStateException("cannot initialize"); })
                .behavior(ActorBehavior.sync((_, _, _) -> ActorEffect.same())).build();
        try (var actors = LocalActorSystem.builder().register(definition).build();
             var simulations = service(actors, definition, 1, new ManualActorScheduler())) {
            actors.start();
            simulations.start();
            for (int i = 0; i < 2; i++) {
                assertInstanceOf(IllegalStateException.class, assertThrows(CompletionException.class,
                        () -> simulations.create().toCompletableFuture().join()).getCause());
            }
            assertTrue(simulations.list().isEmpty());
        }
    }

    @Test
    void shutdownDuringCreationReleasesTheLateInitializedOwner() throws Exception {
        var base = LifeGame.definition(new Random(42));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var definition = ActorDefinition.<LifeGame.State, LifeGame.Command>builder(LifeGame.TYPE)
                .initialState(id -> {
                    entered.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("timeout");
                    } catch (InterruptedException e) { throw new AssertionError(e); }
                    return initial(Long.parseLong(id.key()));
                }).behavior(base.behavior()).build();
        try (var actors = LocalActorSystem.builder().register(definition).build();
             var simulations = service(actors, definition, 1, new ManualActorScheduler())) {
            actors.start();
            simulations.start();
            var creation = CompletableFuture.supplyAsync(simulations::create).thenCompose(stage -> stage);
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                simulations.stop();
            } finally {
                release.countDown();
            }
            assertThrows(java.util.concurrent.ExecutionException.class, () -> creation.get(5, TimeUnit.SECONDS));
            assertTrue(simulations.list().isEmpty());
            // Administrative closure released the runtime cell, including a late owner.
            actors.createOwned(LifeGame.TYPE.id(1L), definition).close();
        }
    }

    @Test
    void stoppingTheServiceClosesAllSimulationsAndRejectsNewCreation() {
        var definition = LifeGame.definition(new Random(42));
        var kit = new ActorTestKit();
        try (var actors = kit.register(definition).start();
             var simulations = service(actors, definition, 2, kit.scheduler())) {
            simulations.start();
            var first = simulations.create().toCompletableFuture();
            var second = simulations.create().toCompletableFuture();
            kit.runAll();
            simulations.stop();
            simulations.stop();
            assertFalse(first.join().snapshot().active());
            assertFalse(second.join().snapshot().active());
            assertEquals(0, kit.scheduler().pendingCount());
            assertTrue(simulations.create().toCompletableFuture().isCompletedExceptionally());
        }
    }

    @Test
    void deletionRejectsAnAdmittedControlAndFailureReleasesCapacity() {
        var base = LifeGame.definition(new Random(42));
        var definition = ActorDefinition.<LifeGame.State, LifeGame.Command>builder(LifeGame.TYPE)
                .initialState(id -> initial(Long.parseLong(id.key())))
                .behavior((context, state, command) -> {
                    if (command instanceof LifeGame.ToggleCell) throw new IllegalStateException("failed");
                    return base.behavior().receive(context, state, command);
                }).build();
        var kit = new ActorTestKit();
        try (var actors = kit.register(definition).start();
             var simulations = service(actors, definition, 1, kit.scheduler())) {
            simulations.start();
            var first = simulations.create().toCompletableFuture();
            kit.runAll();
            var pause = simulations.control(first.join().id(), LifeGame.Action.PAUSE).toCompletableFuture();
            simulations.delete(first.join().id());
            kit.runAll();
            assertTrue(pause.isCompletedExceptionally());
            var second = simulations.create().toCompletableFuture();
            kit.runAll();
            second.join().view().ref().tell(new LifeGame.ToggleCell(0, 0));
            kit.runAll();
            assertEquals(ActorSnapshot.Status.FAILED, second.join().snapshot().status());
            assertTrue(simulations.list().isEmpty());
            var third = simulations.create().toCompletableFuture();
            kit.runAll();
            assertTrue(third.join().snapshot().active());
        }
    }

    @Test
    void creationWaitsForProcessingAndDoesNotPublishAnActorThatStopsDuringStart() {
        var definition = ActorDefinition.<LifeGame.State, LifeGame.Command>builder(LifeGame.TYPE)
                .initialState(id -> initial(Long.parseLong(id.key())))
                .behavior(ActorBehavior.sync((_, state, _) -> ActorEffect.state(state).stopping())).build();
        var kit = new ActorTestKit();
        try (var actors = kit.register(definition).start();
             var simulations = service(actors, definition, 1, kit.scheduler())) {
            simulations.start();
            var creation = simulations.create().toCompletableFuture();
            kit.runAll();
            assertTrue(creation.isCompletedExceptionally());
            assertTrue(simulations.list().isEmpty());
            assertEquals(0, kit.scheduler().pendingCount());
        }
    }

    private static Simulations service(LocalActorSystem actors,
                                       ActorDefinition<LifeGame.State, LifeGame.Command> definition,
                                       int capacity, ActorScheduler deadlines) {
        AtomicLong ids = new AtomicLong();
        return new Simulations(actors, definition, ids::incrementAndGet, capacity, Duration.ofSeconds(2), deadlines);
    }

    private static LifeGame.State initial(long id) {
        return new LifeGame.State(new LifeGame.GameSummary(id, "life", LifeGame.Phase.READY, 0), Board.empty(), 0);
    }
}
