package rsp.actor.ui;

import org.junit.jupiter.api.Test;
import rsp.actor.ActorEnvelope;
import rsp.actor.ActorId;
import rsp.actor.ActorRef;
import rsp.actor.ActorSystem;
import rsp.actor.ActorType;
import rsp.actor.ProcessingReceipt;
import rsp.actor.SendResult;
import rsp.page.PageScope;
import rsp.page.QualifiedSessionId;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class PageActorDirectoryTests {
    private static final ActorType<Long, String> TYPE =
            ActorType.named("page-directory-test", String.class, String::valueOf);
    private static final QualifiedSessionId PAGE = new QualifiedSessionId("device", "page");

    @Test
    void reusesActorWithinPageAndClosesItWithThePage() {
        FakeActors actors = new FakeActors();
        PageActorDirectory<Long, String> directory = PageActorDirectory.numbered(actors, TYPE, () -> "close");
        PageScope scope = new PageScope();

        var first = directory.forPage(PAGE, scope);
        assertSame(first, directory.forPage(PAGE, scope));
        assertThrows(IllegalStateException.class, () -> directory.forPage(PAGE, new PageScope()));
        assertEquals(1L, first.id());
        assertEquals(List.of(first), directory.all());
        assertEquals(first, directory.find(first.id()).orElseThrow());

        scope.close();
        scope.close();
        assertTrue(directory.all().isEmpty());
        assertTrue(directory.find(first.id()).isEmpty());
        assertEquals(List.of("close"), actors.messages(TYPE.id(first.id())));
    }

    @Test
    void staleScopeCannotCloseReplacementActor() {
        FakeActors actors = new FakeActors();
        PageActorDirectory<Long, String> directory = PageActorDirectory.numbered(actors, TYPE, () -> "close");
        PageScope oldScope = new PageScope();
        var oldActor = directory.forPage(PAGE, oldScope);
        directory.close(PAGE);

        PageScope replacementScope = new PageScope();
        var replacement = directory.forPage(PAGE, replacementScope);
        oldScope.close();

        assertNotEquals(oldActor.id(), replacement.id());
        assertEquals(replacement, directory.find(replacement.id()).orElseThrow());
        assertEquals(List.of("close"), actors.messages(TYPE.id(oldActor.id())));
        assertTrue(actors.messages(TYPE.id(replacement.id())).isEmpty());
        replacementScope.close();
        assertEquals(List.of("close"), actors.messages(TYPE.id(replacement.id())));
    }

    @Test
    void duplicateIdIsRejectedWithoutReplacingExistingEntry() {
        FakeActors actors = new FakeActors();
        PageActorDirectory<Long, String> directory = new PageActorDirectory<>(
                actors, TYPE, () -> 1L, () -> "close");
        PageScope firstScope = new PageScope();
        var first = directory.forPage(PAGE, firstScope);

        assertThrows(IllegalStateException.class, () -> directory.forPage(
                new QualifiedSessionId("device", "second"), new PageScope()));
        assertEquals(List.of(first), directory.all());
        firstScope.close();
    }

    @Test
    void closedScopeDoesNotLeaveAnEntryBehind() {
        FakeActors actors = new FakeActors();
        PageActorDirectory<Long, String> directory = PageActorDirectory.numbered(actors, TYPE, () -> "close");
        PageScope scope = new PageScope();
        scope.close();

        assertThrows(IllegalStateException.class, () -> directory.forPage(PAGE, scope));
        assertTrue(directory.all().isEmpty());
        assertEquals(List.of("close"), actors.messages(TYPE.id(1L)));
    }

    private static final class FakeActors implements ActorSystem {
        private final Map<ActorId<?>, FakeRef<?>> refs = new HashMap<>();

        @SuppressWarnings("unchecked")
        @Override
        public synchronized <M> ActorRef<M> ref(ActorId<M> id) {
            return (ActorRef<M>) refs.computeIfAbsent(id, _ -> new FakeRef<>());
        }

        synchronized <M> List<M> messages(ActorId<M> id) {
            @SuppressWarnings("unchecked") FakeRef<M> ref = (FakeRef<M>) refs.get(id);
            return ref == null ? List.of() : List.copyOf(ref.messages);
        }

        @Override
        public <M, R> CompletionStage<R> ask(ActorRef<M> target,
                                              Function<ActorRef<R>, M> command, Duration timeout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <M, R> CompletionStage<R> askEnvelope(ActorRef<M> target,
                                                      Function<ActorRef<R>, ActorEnvelope<M>> command,
                                                      Duration timeout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Void> drainAndStop() {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class FakeRef<M> implements ActorRef<M> {
        private final List<M> messages = new ArrayList<>();

        @Override
        public SendResult tell(ActorEnvelope<M> envelope) {
            messages.add(envelope.message());
            return SendResult.ACCEPTED;
        }

        @Override
        public ProcessingReceipt track(ActorEnvelope<M> envelope) {
            return new ProcessingReceipt(tell(envelope), CompletableFuture.completedFuture(null));
        }
    }
}
