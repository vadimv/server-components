package rsp.actor.testkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import rsp.application.ApplicationContext;
import rsp.actor.ActorBehavior;
import rsp.actor.ActorDefinition;
import rsp.actor.ActorDeliveryException;
import rsp.actor.ActorEffect;
import rsp.actor.ActorEnvelope;
import rsp.actor.ActorRef;
import rsp.actor.ActorType;
import rsp.actor.ActorSystem;
import rsp.actor.ProcessingReceipt;
import rsp.actor.SendResult;
import rsp.actor.MessageId;
import rsp.actor.runtime.LocalActorSystem;
import rsp.actor.runtime.ActorSystemObserver;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LocalActorSystemTests {
    private static final ActorType<String, Integer> COUNTER =
            ActorType.named("counter", Integer.class, key -> key);

    @Test
    void serializesOneKeyWhileAnotherKeyCanRun() {
        ActorTestKit kit = new ActorTestKit();
        CompletableFuture<ActorEffect<Integer>> gate = new CompletableFuture<>();
        ActorProbe<String> events = kit.probe();
        List<String> entered = new ArrayList<>();
        LocalActorSystem system = kit.register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                .initialState(_ -> 0)
                .behavior((context, state, message) -> {
                    entered.add(context.id().key() + ":" + message + ":" + state);
                    if (context.id().key().equals("a") && message == 1) {
                        return gate;
                    }
                    return CompletableFuture.completedFuture(ActorEffect.state(state + message)
                            .send(events, context.id().key() + ":" + (state + message)));
                }).build()).start();
        ActorRef<Integer> a = system.ref(COUNTER, "a");
        ActorRef<Integer> b = system.ref(COUNTER, "b");
        ProcessingReceipt first = a.track(1);
        ProcessingReceipt second = a.track(2);
        ProcessingReceipt independent = b.track(3);
        kit.runAll();
        assertEquals(List.of("a:1:0", "b:3:0"), entered);
        assertFalse(first.processed().toCompletableFuture().isDone());
        assertTrue(independent.processed().toCompletableFuture().isDone());
        gate.complete(ActorEffect.<Integer>state(1).send(events, "a:1"));
        kit.runAll();
        assertEquals(List.of("a:1:0", "b:3:0", "a:2:1"), entered);
        assertEquals(List.of("b:3", "a:1", "a:3"), events.messages());
        assertTrue(first.processed().toCompletableFuture().isDone());
        assertTrue(second.processed().toCompletableFuture().isDone());
        assertTrue(system.drainAndStop().toCompletableFuture().isDone());
    }

    @Test
    void boundsWaitingMailboxAndKeepsAdmissionSeparateFromProcessing() {
        ActorTestKit kit = new ActorTestKit();
        CompletableFuture<ActorEffect<Integer>> gate = new CompletableFuture<>();
        LocalActorSystem system = kit.register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                .initialState(_ -> 0)
                .mailboxCapacity(1)
                .behavior((_, state, message) -> message == 1 ? gate
                        : CompletableFuture.completedFuture(ActorEffect.state(state + message)))
                .build()).start();
        ActorRef<Integer> actor = system.ref(COUNTER, "one");
        ProcessingReceipt first = actor.track(1);
        assertEquals(SendResult.ACCEPTED, first.admission());
        assertEquals(SendResult.MAILBOX_FULL, actor.tell(2));
        kit.runAll();
        assertEquals(SendResult.ACCEPTED, actor.tell(2));
        ProcessingReceipt full = actor.track(3);
        assertEquals(SendResult.MAILBOX_FULL, full.admission());
        assertInstanceOf(ActorDeliveryException.class,
                assertThrows(CompletionException.class,
                        () -> full.processed().toCompletableFuture().join()).getCause());
        gate.complete(ActorEffect.state(1));
        kit.runAll();
        assertTrue(first.processed().toCompletableFuture().isDone());
        system.stop();
    }

    @Test
    void behaviorFailureDiscardsEffectsAndFailsPendingMessages() {
        ActorTestKit kit = new ActorTestKit();
        ActorProbe<Integer> events = kit.probe();
        CompletableFuture<ActorEffect<Integer>> gate = new CompletableFuture<>();
        LocalActorSystem system = kit.register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                .initialState(_ -> 0)
                .behavior((_, state, message) -> message == 1 ? gate
                        : CompletableFuture.completedFuture(ActorEffect.state(state + message)
                                .send(events, state + message)))
                .build()).start();
        ActorRef<Integer> actor = system.ref(COUNTER, "bad");
        ProcessingReceipt first = actor.track(1);
        kit.runAll();
        ProcessingReceipt second = actor.track(2);
        gate.completeExceptionally(new IllegalArgumentException("broken"));
        kit.runAll();
        assertEquals(List.of(), events.messages());
        assertInstanceOf(IllegalArgumentException.class, assertThrows(CompletionException.class,
                () -> first.processed().toCompletableFuture().join()).getCause());
        assertInstanceOf(IllegalArgumentException.class, assertThrows(CompletionException.class,
                () -> second.processed().toCompletableFuture().join()).getCause());
        assertEquals(SendResult.STOPPED, actor.tell(3));
        assertTrue(system.drainAndStop().toCompletableFuture().isDone());
    }

    @Test
    void deliversToAnotherActorAndSchedulesOnlyOneShotMessages() {
        ActorTestKit kit = new ActorTestKit();
        ActorProbe<String> events = kit.probe();
        ActorType<String, String> receiverType = ActorType.named("receiver", String.class, key -> key);
        ActorRef<String>[] destination = refHolder();
        LocalActorSystem system = kit
                .register(ActorDefinition.<Integer, String>builder(receiverType)
                        .initialState(_ -> 0)
                        .behavior(ActorBehavior.sync((_, state, message) ->
                                ActorEffect.<Integer>state(state + 1).send(events, message + ":" + (state + 1))))
                        .build())
                .register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                        .initialState(_ -> 0)
                        .behavior(ActorBehavior.sync((_, state, message) ->
                                ActorEffect.<Integer>state(state + message)
                                        .send(destination[0], "now")
                                        .schedule(destination[0], "later", Duration.ofSeconds(2))))
                        .build()).start();
        destination[0] = system.ref(receiverType, "r");
        system.ref(COUNTER, "sender").tell(1);
        kit.runAll();
        assertEquals(List.of("now:1"), events.messages());
        kit.advance(Duration.ofSeconds(1));
        assertEquals(List.of("now:1"), events.messages());
        kit.advance(Duration.ofSeconds(1));
        assertEquals(List.of("now:1", "later:2"), events.messages());
        system.stop();
    }

    @Test
    void askCompletesOnReplyAndTimesOutWithoutCancellingCommand() {
        ActorTestKit kit = new ActorTestKit();
        ActorType<String, Ask> askType = ActorType.named("ask", Ask.class, key -> key);
        AtomicReference<ActorRef<String>> lateReply = new AtomicReference<>();
        LocalActorSystem system = kit.register(ActorDefinition.<Integer, Ask>builder(askType)
                .initialState(_ -> 0)
                .behavior(ActorBehavior.sync((_, state, command) -> {
                    lateReply.set(command.replyTo());
                    return command.replyNow()
                            ? ActorEffect.<Integer>state(state + 1).reply(command.replyTo(), "ok")
                            : ActorEffect.state(state + 1);
                })).build()).start();
        var target = system.ref(askType, "a");
        var answered = system.<Ask, String>ask(target, reply -> new Ask(true, reply), Duration.ofSeconds(1));
        kit.runAll();
        assertEquals("ok", answered.toCompletableFuture().join());
        assertEquals(0, kit.scheduler().pendingCount());

        var timedOut = system.<Ask, String>ask(target, reply -> new Ask(false, reply), Duration.ofSeconds(1));
        kit.runAll();
        assertFalse(timedOut.toCompletableFuture().isDone());
        kit.advance(Duration.ofSeconds(1));
        assertInstanceOf(TimeoutException.class, assertThrows(CompletionException.class,
                () -> timedOut.toCompletableFuture().join()).getCause());
        assertEquals(SendResult.STOPPED, lateReply.get().tell("late"));
        system.stop();
    }

    @Test
    void drainsAcceptedMessagesAndInternalSendsButRejectsNewExternalWork() {
        ActorTestKit kit = new ActorTestKit();
        ActorProbe<Integer> events = kit.probe();
        ActorRef<Integer>[] destination = refHolder();
        LocalActorSystem system = kit.register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                .initialState(_ -> 0)
                .behavior(ActorBehavior.sync((context, state, message) ->
                        context.id().key().equals("source")
                                ? ActorEffect.<Integer>same().send(destination[0], message)
                                : ActorEffect.<Integer>state(state + message).send(events, message)))
                .build()).start();
        destination[0] = system.ref(COUNTER, "destination");
        ActorRef<Integer> source = system.ref(COUNTER, "source");
        assertEquals(SendResult.ACCEPTED, source.tell(7));
        var stopping = system.drainAndStop().toCompletableFuture();
        assertFalse(stopping.isDone());
        assertEquals(SendResult.STOPPED, source.tell(8));
        kit.runAll();
        assertEquals(List.of(7), events.messages());
        assertTrue(stopping.isDone());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void resolvesInternalDestinationsDuringDrainWhileRejectingExternalSends(boolean existingDestination) {
        ActorTestKit kit = new ActorTestKit();
        ActorProbe<Integer> events = kit.probe();
        AtomicReference<LocalActorSystem> runtime = new AtomicReference<>();
        AtomicReference<ActorRef<Integer>> destination = new AtomicReference<>();
        CompletableFuture<ActorEffect<Integer>> destinationEffect = new CompletableFuture<>();
        LocalActorSystem system = kit.register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                .initialState(_ -> 0)
                .behavior((context, _, message) -> {
                    if (context.id().key().equals("source")) {
                        ActorRef<Integer> resolved = runtime.get().ref(COUNTER, "destination");
                        destination.set(resolved);
                        return CompletableFuture.completedFuture(ActorEffect.<Integer>same()
                                .send(resolved, message));
                    }
                    events.tell(message);
                    return destinationEffect;
                }).build()).start();
        runtime.set(system);
        ActorRef<Integer> previous = existingDestination ? system.ref(COUNTER, "destination") : null;
        try {
            ProcessingReceipt source = system.ref(COUNTER, "source").track(7);
            var drained = system.drainAndStop().toCompletableFuture();
            assertFalse(drained.isDone());

            // Run the source behavior so it resolves the destination during
            // draining, before its outbound effect is applied.
            assertTrue(kit.executor().runNext());
            ActorRef<Integer> resolved = destination.get();
            assertNotNull(resolved);
            assertEquals(SendResult.STOPPED, resolved.tell(8));
            ProcessingReceipt rejected = resolved.track(9);
            assertEquals(SendResult.STOPPED, rejected.admission());
            ActorDeliveryException failure = assertInstanceOf(ActorDeliveryException.class,
                    assertThrows(CompletionException.class,
                            () -> rejected.processed().toCompletableFuture().join()).getCause());
            assertEquals(SendResult.STOPPED, failure.result());

            kit.runAll();
            assertEquals(List.of(7), events.messages());
            assertTrue(source.processed().toCompletableFuture().isDone());
            source.processed().toCompletableFuture().join();
            assertFalse(drained.isDone(), "Drain must wait for the newly resolved destination");
            if (existingDestination) {
                assertSame(previous, resolved);
            }

            destinationEffect.complete(ActorEffect.state(7));
            kit.runAll();
            assertTrue(drained.isDone());
            drained.join();
            assertEquals(SendResult.STOPPED, system.ref(COUNTER, "destination").tell(10));
            assertEquals(List.of(7), events.messages());
        } finally {
            destinationEffect.complete(ActorEffect.same());
            kit.runAll();
            system.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsOfferWaitingForActivationWhenShutdownCompletes(boolean ownedExecutor) throws Exception {
        ActorTestKit kit = new ActorTestKit();
        AtomicInteger processed = new AtomicInteger();
        LocalActorSystem.Builder builder = LocalActorSystem.builder()
                .scheduler(kit.scheduler())
                .register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                        .initialState(_ -> 0)
                        .behavior(ActorBehavior.sync((_, state, message) -> {
                            processed.incrementAndGet();
                            return ActorEffect.state(state + message);
                        })).build());
        if (!ownedExecutor) {
            builder.executor(kit.executor());
        }
        LocalActorSystem system = builder.build();
        system.start();
        ActorRef<Integer> actor = system.ref(COUNTER, "racing");
        CompletableFuture<ProcessingReceipt> offered = new CompletableFuture<>();
        Thread sender = Thread.ofPlatform().daemon().unstarted(() -> {
            try {
                offered.complete(actor.track(1));
            } catch (Throwable failure) {
                offered.completeExceptionally(failure);
            }
        });
        try {
            // Hold the actual mailbox lock to reproduce the gap between the old
            // host admission check and registration of outstanding work.
            var cellsField = LocalActorSystem.class.getDeclaredField("cells");
            cellsField.setAccessible(true);
            Object activation = ((Map<?, ?>) cellsField.get(system)).get(actor.id().orElseThrow());
            synchronized (activation) {
                sender.start();
                awaitBlocked(sender);
                system.drainAndStop().toCompletableFuture().get(5, TimeUnit.SECONDS);
            }

            ProcessingReceipt receipt = offered.get(5, TimeUnit.SECONDS);
            assertEquals(SendResult.STOPPED, receipt.admission());
            ActorDeliveryException failure = assertInstanceOf(ActorDeliveryException.class,
                    assertThrows(CompletionException.class,
                            () -> receipt.processed().toCompletableFuture().join()).getCause());
            assertEquals(SendResult.STOPPED, failure.result());
            kit.runAll();
            assertEquals(0, processed.get());
        } finally {
            sender.join(5_000);
            kit.runAll();
            system.stop();
        }
    }

    @Test
    void drainWaitsForAnAdmittedMessageWhoseExecutorSubmissionIsPaused() throws Exception {
        ActorTestKit kit = new ActorTestKit();
        CountDownLatch submitting = new CountDownLatch(1);
        CountDownLatch resumeSubmission = new CountDownLatch(1);
        AtomicInteger processed = new AtomicInteger();
        LocalActorSystem system = LocalActorSystem.builder()
                .scheduler(kit.scheduler())
                .executor(task -> {
                    submitting.countDown();
                    try {
                        if (!resumeSubmission.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Executor submission was not resumed");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                    kit.executor().execute(task);
                })
                .register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                        .initialState(_ -> 0)
                        .behavior(ActorBehavior.sync((_, state, message) -> {
                            processed.incrementAndGet();
                            return ActorEffect.state(state + message);
                        })).build())
                .build();
        system.start();
        ActorRef<Integer> actor = system.ref(COUNTER, "admitted");
        CompletableFuture<ProcessingReceipt> offered = new CompletableFuture<>();
        Thread sender = Thread.ofPlatform().daemon().start(() -> {
            try {
                offered.complete(actor.track(1));
            } catch (Throwable failure) {
                offered.completeExceptionally(failure);
            }
        });
        try {
            assertTrue(submitting.await(5, TimeUnit.SECONDS));
            var drained = system.drainAndStop().toCompletableFuture();
            assertFalse(drained.isDone());
            assertEquals(SendResult.STOPPED, actor.tell(2));

            resumeSubmission.countDown();
            ProcessingReceipt receipt = offered.get(5, TimeUnit.SECONDS);
            assertEquals(SendResult.ACCEPTED, receipt.admission());
            assertFalse(receipt.processed().toCompletableFuture().isDone());
            assertFalse(drained.isDone());

            kit.runAll();
            receipt.processed().toCompletableFuture().get(5, TimeUnit.SECONDS);
            drained.get(5, TimeUnit.SECONDS);
            assertEquals(1, processed.get());
        } finally {
            resumeSubmission.countDown();
            sender.join(5_000);
            kit.runAll();
            system.stop();
        }
    }

    @Test
    void validatesRegistrationLifecycleAndUnknownTypes() {
        ActorDefinition<Integer, Integer> definition = ActorDefinition.<Integer, Integer>builder(COUNTER)
                .initialState(_ -> 0)
                .behavior(ActorBehavior.sync((_, state, message) -> ActorEffect.state(state + message)))
                .build();
        assertThrows(IllegalArgumentException.class, () -> new ActorTestKit()
                .register(definition).register(definition));
        ActorTestKit kit = new ActorTestKit();
        LocalActorSystem system = kit.register(definition).start();
        ActorType<String, String> missing = ActorType.named("missing", String.class, key -> key);
        assertEquals(SendResult.UNKNOWN_ACTOR, system.ref(missing, "x").tell("hi"));
        assertEquals(SendResult.ACCEPTED, system.ref(COUNTER, "one").tell(1));
        kit.runAll();
        system.stop();
        assertEquals(SendResult.STOPPED, system.ref(COUNTER, "one").tell(2));
        assertThrows(IllegalStateException.class, system::start);
    }

    @Test
    void initializationFailureStopsOnlyThatKeyAndFailsTrackedDelivery() {
        ActorTestKit kit = new ActorTestKit();
        LocalActorSystem system = kit.register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                .initialState(id -> {
                    if (id.key().equals("bad")) {
                        throw new IllegalArgumentException("invalid initial state");
                    }
                    return 0;
                })
                .behavior(ActorBehavior.sync((_, state, message) -> ActorEffect.state(state + message)))
                .build()).start();
        ProcessingReceipt bad = system.ref(COUNTER, "bad").track(1);
        ProcessingReceipt good = system.ref(COUNTER, "good").track(2);
        kit.runAll();
        assertInstanceOf(IllegalArgumentException.class, assertThrows(CompletionException.class,
                () -> bad.processed().toCompletableFuture().join()).getCause());
        assertTrue(good.processed().toCompletableFuture().isDone());
        assertEquals(SendResult.STOPPED, system.ref(COUNTER, "bad").tell(3));
        system.stop();
    }

    @Test
    void selfScheduledMessageIsCancelledOnSystemStop() {
        ActorTestKit kit = new ActorTestKit();
        ActorProbe<Integer> events = kit.probe();
        LocalActorSystem system = kit.register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                .initialState(_ -> 0)
                .behavior(ActorBehavior.sync((context, state, message) ->
                        ActorEffect.<Integer>state(state + 1)
                                .send(events, message)
                                .schedule(context.self(), message + 1, Duration.ofSeconds(1))))
                .build()).start();
        system.ref(COUNTER, "ticking").tell(1);
        kit.runAll();
        assertEquals(1, kit.scheduler().pendingCount());
        kit.advance(Duration.ofSeconds(1));
        assertEquals(List.of(1, 2), events.messages());
        system.stop();
        assertEquals(0, kit.scheduler().pendingCount());
        kit.advance(Duration.ofSeconds(10));
        assertEquals(List.of(1, 2), events.messages());
    }

    @Test
    void stoppingActorCancelsItsOutstandingTimers() {
        ActorTestKit kit = new ActorTestKit();
        ActorProbe<Integer> events = kit.probe();
        LocalActorSystem system = kit.register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                .initialState(_ -> 0)
                .behavior(ActorBehavior.sync((context, state, message) -> message == 0
                        ? ActorEffect.<Integer>same()
                                .schedule(context.self(), 2, Duration.ofSeconds(1))
                        : ActorEffect.<Integer>same().send(events, message).stopping()))
                .build()).start();
        ActorRef<Integer> actor = system.ref(COUNTER, "one");
        actor.tell(0);
        kit.runAll();
        assertEquals(1, kit.scheduler().pendingCount());
        actor.tell(1);
        kit.runAll();
        assertEquals(0, kit.scheduler().pendingCount());
        kit.advance(Duration.ofSeconds(2));
        assertEquals(List.of(1), events.messages());
        system.stop();
    }

    @Test
    void passivatedActorReleasesItsKeyButOldReferencesCannotReactivateIt() {
        ActorTestKit kit = new ActorTestKit();
        ActorProbe<Integer> events = kit.probe();
        LocalActorSystem system = kit.register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                .initialState(_ -> 0)
                .behavior(ActorBehavior.sync((context, state, message) -> message < 0
                        ? ActorEffect.<Integer>same().passivating()
                        : ActorEffect.<Integer>state(state + message)
                                .send(events, state + message)
                                .schedule(context.self(), 1, Duration.ofSeconds(1))))
                .build()).start();
        ActorRef<Integer> old = system.ref(COUNTER, "one");
        old.tell(2);
        kit.runAll();
        assertEquals(1, kit.scheduler().pendingCount());
        old.tell(-1);
        kit.runAll();
        assertEquals(0, kit.scheduler().pendingCount());
        assertEquals(SendResult.STOPPED, old.tell(3));

        ActorRef<Integer> fresh = system.ref(COUNTER, "one");
        fresh.tell(4);
        kit.runAll();
        assertEquals(List.of(2, 4), events.messages());
        system.stop();
    }

    @Test
    void actorStopRejectsPendingWorkAndOutboundRejectionIsObservable() {
        ActorTestKit kit = new ActorTestKit();
        ActorProbe<Integer> closed = kit.probe();
        closed.close();
        List<SendResult> outbound = new ArrayList<>();
        LocalActorSystem system = LocalActorSystem.builder()
                .executor(kit.executor())
                .scheduler(kit.scheduler())
                .observer(new ActorSystemObserver() {
                    @Override
                    public void outboundRejected(rsp.actor.ActorId<?> sender,
                                                 ActorRef<?> recipient, SendResult reason) {
                        outbound.add(reason);
                    }
                })
                .register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                        .initialState(_ -> 0)
                        .behavior(ActorBehavior.sync((_, state, message) ->
                                ActorEffect.<Integer>state(state + message)
                                        .send(closed, message)
                                        .stopping()))
                        .build()).build();
        system.start();
        ActorRef<Integer> actor = system.ref(COUNTER, "one");
        ProcessingReceipt first = actor.track(1);
        ProcessingReceipt pending = actor.track(2);
        kit.runAll();
        assertTrue(first.processed().toCompletableFuture().isDone());
        assertInstanceOf(IllegalStateException.class, assertThrows(CompletionException.class,
                () -> pending.processed().toCompletableFuture().join()).getCause());
        assertEquals(List.of(SendResult.STOPPED), outbound);
        assertEquals(SendResult.STOPPED, actor.tell(3));
        system.stop();
    }

    @Test
    void askWithoutReplyFailsWhenSystemDrains() {
        ActorTestKit kit = new ActorTestKit();
        ActorType<String, Ask> askType = ActorType.named("ask-lifecycle", Ask.class, key -> key);
        LocalActorSystem system = kit.register(ActorDefinition.<Integer, Ask>builder(askType)
                .initialState(_ -> 0)
                .behavior(ActorBehavior.sync((_, state, _) -> ActorEffect.same()))
                .build()).start();
        var answer = system.<Ask, String>ask(system.ref(askType, "one"),
                reply -> new Ask(false, reply), Duration.ofMinutes(1));
        kit.runAll();
        system.stop();
        assertInstanceOf(ActorDeliveryException.class, assertThrows(CompletionException.class,
                () -> answer.toCompletableFuture().join()).getCause());
        assertEquals(0, kit.scheduler().pendingCount());
    }

    @Test
    void productionExecutorDrainsManyKeysWithoutLostReceipts() throws Exception {
        ActorProbe<Integer> events = new ActorProbe<>();
        LocalActorSystem system = LocalActorSystem.builder()
                .register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                        .initialState(_ -> 0)
                        .behavior(ActorBehavior.sync((_, state, message) ->
                                ActorEffect.<Integer>state(state + message).send(events, state + message)))
                        .build()).build();
        system.start();
        List<CompletableFuture<Void>> processed = new ArrayList<>();
        for (int index = 0; index < 500; index++) {
            processed.add(system.ref(COUNTER, "key-" + index % 10)
                    .track(1).processed().toCompletableFuture());
        }
        system.drainAndStop().toCompletableFuture().get(5, TimeUnit.SECONDS);
        CompletableFuture.allOf(processed.toArray(CompletableFuture[]::new))
                .get(5, TimeUnit.SECONDS);
        assertEquals(500, events.messages().size());
        assertEquals(10, events.messages().stream().filter(value -> value == 50).count());
    }

    @Test
    void applicationContextOwnsActorSystemLifecycle() {
        ActorTestKit kit = new ActorTestKit();
        LocalActorSystem system = LocalActorSystem.builder()
                .executor(kit.executor())
                .scheduler(kit.scheduler())
                .register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                        .initialState(_ -> 0)
                        .behavior(ActorBehavior.sync((_, state, message) -> ActorEffect.state(state + message)))
                        .build()).build();
        ApplicationContext application = ApplicationContext.builder()
                .service(ActorSystem.class, system)
                .build();
        assertEquals(SendResult.NOT_STARTED, system.ref(COUNTER, "one").tell(1));
        application.start();
        ProcessingReceipt accepted = application.require(ActorSystem.class)
                .ref(COUNTER, "one").track(1);
        kit.runAll();
        assertTrue(accepted.processed().toCompletableFuture().isDone());
        application.stop();
        assertEquals(SendResult.STOPPED, system.ref(COUNTER, "one").tell(2));
    }

    @Test
    void lifecycleObserverReportsStartDrainAndStopWithoutChangingDelivery() {
        ActorTestKit kit = new ActorTestKit();
        List<String> events = new ArrayList<>();
        LocalActorSystem system = LocalActorSystem.builder()
                .executor(kit.executor())
                .scheduler(kit.scheduler())
                .observer(new ActorSystemObserver() {
                    @Override
                    public void systemStarted() {
                        events.add("started");
                        throw new IllegalStateException("observer failure");
                    }

                    @Override
                    public void systemStopping() {
                        events.add("stopping");
                    }

                    @Override
                    public void systemStopped(Throwable failure) {
                        assertNull(failure);
                        events.add("stopped");
                    }
                })
                .register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                        .initialState(_ -> 0)
                        .behavior(ActorBehavior.sync((_, state, message) ->
                                ActorEffect.state(state + message)))
                        .build()).build();
        ApplicationContext application = ApplicationContext.builder()
                .service(ActorSystem.class, system)
                .build();
        application.start();
        var receipt = system.ref(COUNTER, "one").track(1);
        var stopping = system.drainAndStop();
        assertEquals(List.of("started", "stopping"), events);
        kit.runAll();
        stopping.toCompletableFuture().join();
        application.stop();
        assertTrue(receipt.processed().toCompletableFuture().isDone());
        assertEquals(List.of("started", "stopping", "stopped"), events);
    }

    @Test
    void askPropagatesBehaviorFailureAndPreservesSuppliedEnvelope() {
        ActorTestKit kit = new ActorTestKit();
        MessageId supplied = new MessageId("http-request-42");
        AtomicReference<MessageId> observed = new AtomicReference<>();
        ActorType<String, Ask> type = ActorType.named("ask-failure", Ask.class, key -> key);
        LocalActorSystem system = kit.register(ActorDefinition.<Integer, Ask>builder(type)
                .initialState(_ -> 0)
                .behavior(ActorBehavior.sync((context, _, _) -> {
                    observed.set(context.envelope().messageId());
                    throw new IllegalArgumentException("failed actor command");
                })).build()).start();
        var answer = system.<Ask, String>askEnvelope(system.ref(type, "one"),
                reply -> new ActorEnvelope<>(supplied, new Ask(false, reply),
                        Optional.empty(), Optional.empty()), Duration.ofSeconds(1));
        kit.runAll();
        assertEquals(supplied, observed.get());
        assertEquals("failed actor command", assertThrows(CompletionException.class,
                () -> answer.toCompletableFuture().join()).getCause().getMessage());
        assertEquals(0, kit.scheduler().pendingCount());
        system.stop();
    }

    @Test
    void explicitOutboundEnvelopePreservesCorrelationMetadata() {
        ActorTestKit kit = new ActorTestKit();
        ActorProbe<String> probe = kit.probe();
        MessageId root = new MessageId("root-message");
        MessageId outbound = new MessageId("outbound-message");
        LocalActorSystem system = kit.register(ActorDefinition.<Integer, Integer>builder(COUNTER)
                .initialState(_ -> 0)
                .behavior(ActorBehavior.sync((context, state, _) ->
                        ActorEffect.<Integer>same().sendEnvelope(probe,
                                new ActorEnvelope<>(outbound, "changed",
                                        Optional.of(root),
                                        Optional.of(context.envelope().messageId())))))
                .build()).start();
        system.ref(COUNTER, "one").tell(new ActorEnvelope<>(root, 1,
                Optional.empty(), Optional.empty()));
        kit.runAll();
        assertEquals(outbound, probe.envelopes().getFirst().messageId());
        assertEquals(Optional.of(root), probe.envelopes().getFirst().correlationId());
        assertEquals(Optional.of(root), probe.envelopes().getFirst().causationId());
        system.stop();
    }

    private record Ask(boolean replyNow, ActorRef<String> replyTo) { }

    private static void awaitBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertEquals(Thread.State.BLOCKED, thread.getState(), "Sender must reach the activation lock");
    }

    @SuppressWarnings("unchecked")
    private static <M> ActorRef<M>[] refHolder() {
        return (ActorRef<M>[]) new ActorRef<?>[1];
    }
}
