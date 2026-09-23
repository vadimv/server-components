package rsp.actor.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

        @Override
        public Cancellation schedule(Duration delay, Runnable task) {
            Scheduled scheduled = new Scheduled(task);
            pending.add(scheduled);
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
