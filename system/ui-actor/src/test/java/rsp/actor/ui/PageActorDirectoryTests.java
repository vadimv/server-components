package rsp.actor.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rsp.actor.ActorBehavior;
import rsp.actor.ActorDefinition;
import rsp.actor.ActorEffect;
import rsp.actor.ActorType;
import rsp.actor.SendResult;
import rsp.component.CommandsEnqueue;
import rsp.page.PageScope;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;
import rsp.page.events.GenericTaskEvent;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageActorDirectoryTests {
    private static final ActorType<Long, String> TYPE =
            ActorType.named("page-directory-test", String.class, String::valueOf);
    private static final ActorDefinition<List<String>, String> DEFINITION =
            ActorDefinition.<List<String>, String>builder(TYPE)
                    .initialState(_ -> List.of())
                    .behavior(ActorBehavior.sync((_, state, message) -> {
                        var next = new java.util.ArrayList<>(state);
                        next.add(message);
                        return ActorEffect.state(List.copyOf(next));
                    }))
                    .build();
    private static final QualifiedSessionId PAGE = new QualifiedSessionId("device", "page");

    private PageActorRuntime runtime;
    private TestCommands commands;

    @BeforeEach
    void startRuntime() {
        runtime = PageActorRuntime.builder().build();
        runtime.start();
        commands = new TestCommands();
    }

    @AfterEach
    void stopRuntime() {
        runtime.stop();
    }

    @Test
    void reusesActorWithinPageAndClosesItWithThePage() {
        PageActorDirectory<Long, String> directory = PageActorDirectory.numbered(runtime, TYPE);
        PageScope scope = new PageScope();

        var first = directory.activate(PAGE, scope, commands, DEFINITION);
        assertSame(first, directory.activate(PAGE, scope, commands, DEFINITION));
        assertThrows(IllegalStateException.class, () -> directory.activate(
                PAGE, new PageScope(), commands, DEFINITION));
        assertEquals("1", first.id().key());
        assertEquals(List.of(first.ref()), directory.all().stream()
                .map(PageActorDirectory.Entry::ref).toList());
        assertEquals(first.ref(), directory.find(1L).orElseThrow().ref());

        assertEquals(SendResult.ACCEPTED, first.ref().tell("one"));
        commands.drain();
        assertEquals(List.of("one"), first.state());

        scope.close();
        scope.close();
        assertTrue(directory.all().isEmpty());
        assertTrue(directory.find(1L).isEmpty());
        assertEquals(SendResult.STOPPED, first.ref().tell("late"));
    }

    @Test
    void staleScopeCannotCloseReplacementActor() {
        PageActorDirectory<Long, String> directory = PageActorDirectory.numbered(runtime, TYPE);
        PageScope oldScope = new PageScope();
        var oldActor = directory.activate(PAGE, oldScope, commands, DEFINITION);
        directory.close(PAGE);

        PageScope replacementScope = new PageScope();
        var replacement = directory.activate(PAGE, replacementScope, commands, DEFINITION);
        oldScope.close();

        assertNotEquals(oldActor.id(), replacement.id());
        assertEquals(replacement.ref(), directory.find(2L).orElseThrow().ref());
        assertEquals(SendResult.STOPPED, oldActor.ref().tell("late"));
        assertEquals(SendResult.ACCEPTED, replacement.ref().tell("live"));
        replacementScope.close();
        assertEquals(SendResult.STOPPED, replacement.ref().tell("late"));
    }

    @Test
    void duplicateIdIsRejectedWithoutReplacingExistingEntry() {
        PageActorDirectory<Long, String> directory = new PageActorDirectory<>(
                runtime, TYPE, () -> 1L);
        PageScope firstScope = new PageScope();
        var first = directory.activate(PAGE, firstScope, commands, DEFINITION);

        assertThrows(IllegalStateException.class, () -> directory.activate(
                new QualifiedSessionId("device", "second"), new PageScope(),
                commands, DEFINITION));
        assertEquals(first.ref(), directory.find(1L).orElseThrow().ref());
        firstScope.close();
    }

    @Test
    void closedScopeDoesNotLeaveAnEntryBehind() {
        PageActorDirectory<Long, String> directory = PageActorDirectory.numbered(runtime, TYPE);
        PageScope scope = new PageScope();
        scope.close();

        assertThrows(IllegalStateException.class, () ->
                directory.activate(PAGE, scope, commands, DEFINITION));
        assertTrue(directory.all().isEmpty());
    }

    private static final class TestCommands implements CommandsEnqueue {
        private final Queue<Command> pending = new ArrayDeque<>();

        @Override
        public void offer(Command command) {
            pending.add(command);
        }

        private void drain() {
            Command command;
            while ((command = pending.poll()) != null) {
                if (command instanceof GenericTaskEvent task) {
                    task.task().run();
                } else {
                    throw new AssertionError("Unexpected command: " + command);
                }
            }
        }
    }
}
