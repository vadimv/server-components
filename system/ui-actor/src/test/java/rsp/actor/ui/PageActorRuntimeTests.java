package rsp.actor.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import rsp.actor.ActorBehavior;
import rsp.actor.ActorDefinition;
import rsp.actor.ActorEffect;
import rsp.actor.ActorRef;
import rsp.actor.ActorType;
import rsp.actor.SendResult;
import rsp.actor.runtime.ActorScheduler;
import rsp.component.CommandsEnqueue;
import rsp.page.PageScope;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;
import rsp.page.events.GenericTaskEvent;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageActorRuntimeTests {
    private static final ActorType<Long, Message> TYPE =
            ActorType.named("page-runtime-test", Message.class, String::valueOf);
    private static final QualifiedSessionId PAGE = new QualifiedSessionId("device", "page");

    private ManualScheduler scheduler;
    private PageActorRuntime runtime;
    private TestCommands commands;
    private PageScope scope;

    @BeforeEach
    void start() {
        scheduler = new ManualScheduler();
        runtime = PageActorRuntime.builder().scheduler(scheduler).build();
        runtime.start();
        commands = new TestCommands();
        scope = new PageScope();
    }

    @AfterEach
    void stop() {
        scope.close();
        runtime.stop();
    }

    @Test
    void processesOneTurnPerPageTaskAndPreservesMailboxOrder() {
        var directory = PageActorDirectory.numbered(runtime, TYPE);
        var handle = directory.activate(PAGE, scope, commands, countingDefinition(4));

        var first = handle.ref().track(new Add(1));
        var second = handle.ref().track(new Add(2));
        assertEquals(1, commands.size());
        assertFalse(first.processed().toCompletableFuture().isDone());

        commands.runOne();
        assertEquals(1, commands.size());
        assertEquals(0, handle.state());
        commands.runOne();
        assertTrue(first.processed().toCompletableFuture().isDone());
        assertFalse(second.processed().toCompletableFuture().isDone());
        assertEquals(1, handle.state());
        assertEquals(1, commands.size());

        commands.drain();
        assertEquals(3, handle.state());
        assertTrue(second.processed().toCompletableFuture().isDone());
    }

    @Test
    void incompleteBehaviorLeavesThePageQueueFreeAndCompletionReturnsThroughIt() {
        CompletableFuture<ActorEffect<Integer>> pending = new CompletableFuture<>();
        ActorDefinition<Integer, Message> definition = ActorDefinition.<Integer, Message>builder(TYPE)
                .initialState(_ -> 10)
                .behavior((_, _, _) -> pending)
                .build();
        var handle = PageActorDirectory.numbered(runtime, TYPE)
                .activate(PAGE, scope, commands, definition);

        var receipt = handle.ref().track(new Add(5));
        commands.runOne();
        assertEquals(0, commands.size());
        assertEquals(10, handle.state());

        pending.complete(ActorEffect.state(15));
        assertEquals(1, commands.size());
        assertFalse(receipt.processed().toCompletableFuture().isDone());
        commands.runOne();
        assertEquals(15, handle.state());
        assertTrue(receipt.processed().toCompletableFuture().isDone());
    }

    @Test
    void closeFailsCurrentAndQueuedReceiptsAndLateCompletionCannotResurrectActor() {
        CompletableFuture<ActorEffect<Integer>> pending = new CompletableFuture<>();
        ActorDefinition<Integer, Message> definition = ActorDefinition.<Integer, Message>builder(TYPE)
                .initialState(_ -> 0)
                .behavior((_, _, _) -> pending)
                .build();
        var directory = PageActorDirectory.numbered(runtime, TYPE);
        var handle = directory.activate(PAGE, scope, commands, definition);
        var current = handle.ref().track(new Add(1));
        var queued = handle.ref().track(new Add(2));
        commands.runOne();

        scope.close();
        assertTrue(current.processed().toCompletableFuture().isCompletedExceptionally());
        assertTrue(queued.processed().toCompletableFuture().isCompletedExceptionally());
        assertEquals(SendResult.STOPPED, handle.ref().tell(new Add(3)));
        assertTrue(directory.all().isEmpty());

        pending.complete(ActorEffect.state(99));
        commands.drain();
        assertEquals(0, handle.state());
    }

    @Test
    void askAndDelayedSelfMessagesUseTheSamePageHostedActivation() {
        var directory = PageActorDirectory.numbered(runtime, TYPE);
        var handle = directory.activate(PAGE, scope, commands, countingDefinition(8));

        var answer = runtime.<Message, Integer>ask(handle.ref(),
                replyTo -> new Read(replyTo), Duration.ofSeconds(1));
        commands.drain();
        assertEquals(0, answer.toCompletableFuture().join());

        handle.ref().tell(new StartDelayed(7));
        commands.drain();
        assertEquals(1, scheduler.pendingCount());
        scheduler.runAll();
        commands.drain();
        assertEquals(7, handle.state());

        handle.ref().tell(new StartDelayed(10));
        commands.drain();
        assertEquals(1, scheduler.pendingCount());
        scope.close();
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void closingPageFromReplyCancelsTimersAndSuppressesRemainingEffects() {
        var directory = PageActorDirectory.numbered(runtime, TYPE);
        try (PageScope recipientScope = new PageScope()) {
            var recipient = directory.activate(new QualifiedSessionId("device", "recipient"),
                    recipientScope, commands, countingDefinition(8));
            ActorDefinition<Integer, Message> definition = ActorDefinition.<Integer, Message>builder(TYPE)
                    .initialState(_ -> 0)
                    .behavior(ActorBehavior.sync((_, _, message) -> ActorEffect.<Integer>same()
                            .schedule(recipient.ref(), new Add(1), Duration.ofSeconds(1))
                            .reply(((Read) message).replyTo(), 42)
                            .schedule(recipient.ref(), new Add(2), Duration.ofSeconds(1))
                            .send(recipient.ref(), new Add(3))))
                    .build();
            var sender = directory.activate(PAGE, scope, commands, definition);
            var answer = runtime.<Message, Integer>ask(sender.ref(), Read::new, Duration.ofSeconds(2));
            var closed = answer.thenRun(scope::close);

            commands.drain();
            closed.toCompletableFuture().join();
            assertEquals(42, answer.toCompletableFuture().join());
            assertEquals(SendResult.STOPPED, sender.ref().tell(new Add(1)));
            assertEquals(1, directory.all().size());
            assertEquals(0, scheduler.pendingCount());
            scheduler.runAll();
            commands.drain();
            assertEquals(0, recipient.state());

            // Closing one owner must leave other actors' timers usable.
            recipient.ref().tell(new StartDelayed(7));
            commands.drain();
            assertEquals(1, scheduler.pendingCount());
            scheduler.runAll();
            commands.drain();
            assertEquals(7, recipient.state());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closeDuringTimerRegistrationCancelsHandleWhenItArrives(boolean concurrentClose) throws Exception {
        var handle = PageActorDirectory.numbered(runtime, TYPE)
                .activate(PAGE, scope, commands, countingDefinition(8));
        var receipt = handle.ref().track(new StartDelayed(7));
        if (!concurrentClose) {
            scheduler.beforeReturn = scope::close;
            commands.drain();
        } else {
            CountDownLatch registering = new CountDownLatch(1);
            CountDownLatch resumeRegistration = new CountDownLatch(1);
            scheduler.beforeReturn = () -> {
                registering.countDown();
                try {
                    if (!resumeRegistration.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timer registration was not resumed");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            };
            try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
                var processing = workers.submit(commands::drain);
                try {
                    assertTrue(registering.await(5, TimeUnit.SECONDS));
                    // Closure must finish even before schedule() returns its handle.
                    workers.submit(scope::close).get(5, TimeUnit.SECONDS);
                } finally {
                    resumeRegistration.countDown();
                    processing.get(5, TimeUnit.SECONDS);
                }
            }
        }
        assertTrue(receipt.processed().toCompletableFuture().isCompletedExceptionally());
        assertEquals(0, scheduler.pendingCount());
        scheduler.runAll();
        commands.drain();
        assertEquals(0, handle.state());
        assertEquals(SendResult.STOPPED, handle.ref().tell(new Add(1)));
    }

    @Test
    void cancelledCallbackCannotDeliverAfterTheOwnerIdIsReused() {
        var directory = new PageActorDirectory<>(runtime, TYPE, () -> 1L);
        try (PageScope recipientScope = new PageScope(); PageScope replacementScope = new PageScope()) {
            var recipient = runtime.activate(TYPE.id(2L), countingDefinition(8),
                    commands, recipientScope, () -> { });
            ActorDefinition<Integer, Message> definition = ActorDefinition.<Integer, Message>builder(TYPE)
                    .initialState(_ -> 0)
                    .behavior(ActorBehavior.sync((_, _, message) -> ActorEffect.<Integer>same()
                            .schedule(recipient.ref(), message, Duration.ofSeconds(1))))
                    .build();
            var original = directory.activate(PAGE, scope, commands, definition);
            original.ref().tell(new Add(7));
            commands.drain();
            Runnable alreadyQueuedCallback = scheduler.pending.getFirst().task;

            scope.close();
            var replacement = directory.activate(PAGE, replacementScope, commands, definition);
            assertEquals(original.id(), replacement.id());
            replacement.ref().tell(new Add(3));
            commands.drain();
            assertEquals(1, scheduler.pendingCount());

            // A scheduler may already have queued the callback when cancelled.
            alreadyQueuedCallback.run();
            scheduler.runAll();
            commands.drain();
            assertEquals(3, recipient.state());
        }
    }

    @Test
    void stoppingEffectStillRepliesBeforeCancellingItsTimers() {
        ActorDefinition<Integer, Message> definition = ActorDefinition.<Integer, Message>builder(TYPE)
                .initialState(_ -> 0)
                .behavior(ActorBehavior.sync((context, _, message) -> ActorEffect.<Integer>same()
                        .reply(((Read) message).replyTo(), 42)
                        .schedule(context.self(), new Add(1), Duration.ofSeconds(1))
                        .stopping()))
                .build();
        var directory = PageActorDirectory.numbered(runtime, TYPE);
        var handle = directory.activate(PAGE, scope, commands, definition);
        var answer = runtime.<Message, Integer>ask(handle.ref(), Read::new, Duration.ofSeconds(2));
        commands.drain();
        assertEquals(42, answer.toCompletableFuture().join());
        assertEquals(0, scheduler.pendingCount());
        assertTrue(directory.all().isEmpty());
    }

    @Test
    void mailboxCapacityRejectsOnlyWaitingMessages() {
        CompletableFuture<ActorEffect<Integer>> pending = new CompletableFuture<>();
        ActorDefinition<Integer, Message> definition = ActorDefinition.<Integer, Message>builder(TYPE)
                .initialState(_ -> 0)
                .behavior((_, _, _) -> pending)
                .mailboxCapacity(1)
                .build();
        var handle = PageActorDirectory.numbered(runtime, TYPE)
                .activate(PAGE, scope, commands, definition);

        assertEquals(SendResult.ACCEPTED, handle.ref().tell(new Add(1)));
        commands.runOne();
        assertEquals(SendResult.ACCEPTED, handle.ref().tell(new Add(2)));
        assertEquals(SendResult.MAILBOX_FULL, handle.ref().tell(new Add(3)));
    }

    private static ActorDefinition<Integer, Message> countingDefinition(int mailboxCapacity) {
        return ActorDefinition.<Integer, Message>builder(TYPE)
                .initialState(_ -> 0)
                .behavior(ActorBehavior.sync((context, state, message) -> switch (message) {
                    case Add add -> ActorEffect.state(state + add.amount());
                    case Read read -> ActorEffect.<Integer>same().reply(read.replyTo(), state);
                    case StartDelayed delayed -> ActorEffect.<Integer>same().schedule(
                            context.self(), new Add(delayed.amount()), Duration.ofMillis(10));
                }))
                .mailboxCapacity(mailboxCapacity)
                .build();
    }

    private sealed interface Message permits Add, Read, StartDelayed { }

    private record Add(int amount) implements Message { }

    private record Read(ActorRef<Integer> replyTo) implements Message { }

    private record StartDelayed(int amount) implements Message { }

    private static final class TestCommands implements CommandsEnqueue {
        private final Queue<Command> pending = new ArrayDeque<>();

        @Override
        public void offer(Command command) {
            pending.add(command);
        }

        private int size() {
            return pending.size();
        }

        private void runOne() {
            Command command = pending.remove();
            assertTrue(command instanceof GenericTaskEvent);
            ((GenericTaskEvent) command).task().run();
        }

        private void drain() {
            while (!pending.isEmpty()) {
                runOne();
            }
        }
    }

    private static final class ManualScheduler implements ActorScheduler {
        private final List<Scheduled> pending = new ArrayList<>();
        private Runnable beforeReturn = () -> { };

        @Override
        public Cancellation schedule(Duration delay, Runnable task) {
            Scheduled scheduled = new Scheduled(task);
            pending.add(scheduled);
            beforeReturn.run();
            return scheduled;
        }

        private int pendingCount() {
            return (int) pending.stream().filter(task -> !task.cancelled).count();
        }

        private void runAll() {
            List<Scheduled> ready = List.copyOf(pending);
            pending.clear();
            ready.stream().filter(task -> !task.cancelled).forEach(task -> task.task.run());
        }

        private static final class Scheduled implements Cancellation {
            private final Runnable task;
            private boolean cancelled;

            private Scheduled(Runnable task) {
                this.task = task;
            }

            @Override
            public void cancel() {
                cancelled = true;
            }
        }
    }
}
