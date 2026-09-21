package rsp.app.gameoflife;

import org.junit.jupiter.api.Test;
import rsp.actor.ActorRef;
import rsp.actor.runtime.LocalActorSystem;
import rsp.actor.ui.PageActorDirectory;
import rsp.actor.testkit.ActorProbe;
import rsp.actor.testkit.ActorTestKit;
import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentContext;
import rsp.component.ContextKey;
import rsp.component.StateUpdater;
import rsp.dom.TreePositionPath;
import rsp.http.HttpHeaders;
import rsp.http.HttpMethod;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;
import rsp.http.RequestBody;
import rsp.page.QualifiedSessionId;
import rsp.page.PageBuilder;
import rsp.page.PageScope;
import rsp.page.RedirectableEventsConsumer;
import rsp.url.Path;
import rsp.url.Query;
import rsp.util.json.Json;
import rsp.util.json.JsonDataType;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

class LifeGameTests {
    @Test
    void boardAdvancesWithoutMutatingPublishedSnapshots() {
        Board before = Board.empty().toggle(1, 1).toggle(2, 1).toggle(3, 1);
        Board after = before.advance();
        assertTrue(before.isAlive(1 + Board.WIDTH));
        assertFalse(after.isAlive(1 + Board.WIDTH));
        assertTrue(after.isAlive(2));
        assertTrue(after.isAlive(2 + Board.WIDTH));
        assertTrue(after.isAlive(2 + Board.WIDTH * 2));
    }

    @Test
    void subscribersReceiveInitialSnapshotAndOnlyLiveSubscribersReceiveChanges() {
        Fixture fixture = new Fixture();
        ActorProbe<LifeGame.Snapshot> first = fixture.kit.probe();
        ActorProbe<LifeGame.Snapshot> second = fixture.kit.probe();
        fixture.game.tell(new LifeGame.Subscribe(first));
        fixture.game.tell(new LifeGame.Subscribe(second));
        fixture.kit.runAll();
        assertEquals(LifeGame.Phase.READY, first.messages().getFirst().summary().status());
        assertEquals(fixture.sessionGame.id(), first.messages().getFirst().summary().id());
        assertEquals(1, second.messages().size());

        fixture.game.tell(new LifeGame.ToggleCell(2, 3));
        fixture.kit.runAll();
        assertTrue(first.messages().getLast().board().isAlive(3 * Board.WIDTH + 2));
        assertFalse(first.messages().getFirst().board().isAlive(3 * Board.WIDTH + 2));
        assertEquals(2, second.messages().size());

        fixture.game.tell(new LifeGame.Unsubscribe(first));
        fixture.game.tell(LifeGame.Control.of(LifeGame.Action.RESET));
        fixture.kit.runAll();
        assertEquals(2, first.messages().size());
        assertEquals(3, second.messages().size());
        assertFalse(second.messages().getLast().board().isAlive(3 * Board.WIDTH + 2));
        fixture.actors.stop();
    }

    @Test
    void pauseAndResetInvalidateOldTicksAndControlsAreIdempotent() {
        Fixture fixture = new Fixture();
        ActorProbe<LifeGame.GameSummary> replies = fixture.kit.probe();
        fixture.game.tell(LifeGame.Control.replying(LifeGame.Action.START, replies));
        fixture.kit.runAll();
        assertEquals(LifeGame.Phase.RUNNING, replies.messages().getLast().status());
        assertEquals(1, fixture.kit.scheduler().pendingCount());
        fixture.game.tell(LifeGame.Control.of(LifeGame.Action.START));
        fixture.kit.runAll();
        assertEquals(1, fixture.kit.scheduler().pendingCount());

        fixture.game.tell(LifeGame.Control.of(LifeGame.Action.PAUSE));
        fixture.game.tell(LifeGame.Control.of(LifeGame.Action.START));
        fixture.kit.runAll();
        fixture.kit.advance(LifeGame.TICK_INTERVAL);
        fixture.game.tell(new LifeGame.Status(replies));
        fixture.kit.runAll();
        assertEquals(1, replies.messages().getLast().generation());

        fixture.game.tell(LifeGame.Control.of(LifeGame.Action.RESET));
        fixture.kit.runAll();
        fixture.kit.advance(LifeGame.TICK_INTERVAL.multipliedBy(3));
        fixture.game.tell(new LifeGame.Status(replies));
        fixture.kit.runAll();
        assertEquals(LifeGame.Phase.READY, replies.messages().getLast().status());
        assertEquals(0, replies.messages().getLast().generation());
        assertEquals(0, fixture.kit.scheduler().pendingCount());
        fixture.actors.stop();
    }

    @Test
    void statusAndControlRoutesUseNumericActiveGameIdsAndDoNotCreateUnknownActors() {
        Fixture fixture = new Fixture();
        var router = LifeRoutes.router(fixture.actors, fixture.games);
        var catalog = router.handle(request(HttpMethod.GET, "/api/games"));
        fixture.kit.runAll();
        HttpResponse listed = catalog.toCompletableFuture().join();
        JsonDataType.Array games = (JsonDataType.Array) Json.parse(read(listed));
        assertEquals(1, games.elements().length);
        assertEquals(fixture.sessionGame.id(), Json.requireObject(games.elements()[0])
                .requiredNumber("id").asLong());

        HttpResponse unknown = router.handle(request(HttpMethod.GET, "/api/games/missing"))
                .toCompletableFuture().join();
        assertEquals(HttpStatus.NOT_FOUND, unknown.status());
        assertEquals(HttpStatus.NOT_FOUND, router.handle(request(HttpMethod.GET,
                "/api/games/999999999999999999999"))
                .toCompletableFuture().join().status());

        var started = router.handle(request(HttpMethod.POST,
                "/api/games/" + fixture.sessionGame.id() + "/start"));
        fixture.kit.runAll();
        assertEquals("RUNNING", Json.requireObject(Json.parse(read(started.toCompletableFuture().join())))
                .requiredString("status"));
        var paused = router.handle(request(HttpMethod.POST,
                "/api/games/" + fixture.sessionGame.id() + "/pause"));
        fixture.kit.runAll();
        assertEquals("PAUSED", Json.requireObject(Json.parse(read(paused.toCompletableFuture().join())))
                .requiredString("status"));
        fixture.games.close(Fixture.SESSION);
        fixture.kit.runAll();
        assertEquals(HttpStatus.NOT_FOUND, router.handle(request(HttpMethod.GET,
                "/api/games/" + fixture.sessionGame.id())).toCompletableFuture().join().status());
        var empty = router.handle(request(HttpMethod.GET, "/api/games"));
        assertEquals(0, ((JsonDataType.Array) Json.parse(read(empty.toCompletableFuture().join()))).elements().length);
        fixture.actors.stop();
    }

    @Test
    void componentMountProjectsActorSnapshotsAndUnmountStopsDelivery() {
        Fixture fixture = new Fixture();
        LifeComponent component = new LifeComponent(fixture.games);
        ComponentCompositeKey componentId = new ComponentCompositeKey(
                Fixture.SESSION, LifeComponent.class, TreePositionPath.of("1"));
        RecordingUpdater updater = new RecordingUpdater();
        PageBuilder builder = new PageBuilder(Fixture.SESSION, Optional.empty(),
                new ComponentContext().with(new ContextKey.ClassKey<>(PageScope.class), fixture.scope),
                new RedirectableEventsConsumer());
        var segment = component.createComponentSegment(Fixture.SESSION, TreePositionPath.of("1"),
                builder, new ComponentContext().with(new ContextKey.ClassKey<>(PageScope.class), fixture.scope),
                new RedirectableEventsConsumer());

        component.onMounted(segment, componentId, updater.state, new RedirectableEventsConsumer(), updater);
        fixture.kit.runAll();
        updater.runAll();
        assertEquals(LifeGame.Phase.READY, updater.state.snapshot().orElseThrow().summary().status());

        component.onIntentDispatched(new LifeComponent.Toggle(2, 3), updater.state, updater);
        fixture.kit.runAll();
        updater.runAll();
        assertTrue(updater.state.snapshot().orElseThrow().board().isAlive(3 * Board.WIDTH + 2));

        segment.unmount();
        fixture.kit.runAll();
        assertEquals(rsp.actor.SendResult.ACCEPTED,
                fixture.game.tell(new LifeGame.ToggleCell(4, 5)));
        fixture.scope.close();
        fixture.kit.runAll();
        assertEquals(rsp.actor.SendResult.STOPPED,
                fixture.game.tell(new LifeGame.ToggleCell(5, 5)));
        fixture.kit.runAll();
        updater.runAll();
        assertFalse(updater.state.snapshot().orElseThrow().board().isAlive(5 * Board.WIDTH + 4));
        fixture.actors.stop();
    }

    @Test
    void separatePageSessionsReceiveSeparateGameActors() {
        Fixture fixture = new Fixture();
        assertEquals(fixture.sessionGame.id(), fixture.games.forPage(Fixture.SESSION, fixture.scope).id());
        PageActorDirectory.Entry<Long, LifeGame.Command> other = fixture.games.forPage(
                new QualifiedSessionId("device", "two"), new PageScope());
        assertNotEquals(fixture.sessionGame.id(), other.id());
        ActorProbe<LifeGame.Snapshot> first = fixture.kit.probe();
        ActorProbe<LifeGame.Snapshot> second = fixture.kit.probe();
        fixture.game.tell(new LifeGame.Subscribe(first));
        other.ref().tell(new LifeGame.Subscribe(second));
        fixture.kit.runAll();

        fixture.game.tell(new LifeGame.ToggleCell(2, 3));
        fixture.kit.runAll();
        assertTrue(first.messages().getLast().board().isAlive(3 * Board.WIDTH + 2));
        assertFalse(second.messages().getLast().board().isAlive(3 * Board.WIDTH + 2));

        fixture.games.close(Fixture.SESSION);
        fixture.kit.runAll();
        assertEquals(rsp.actor.SendResult.STOPPED, fixture.game.tell(new LifeGame.ToggleCell(1, 1)));
        assertNotEquals(fixture.sessionGame.id(), fixture.games.forPage(Fixture.SESSION, fixture.scope).id());
        assertEquals(rsp.actor.SendResult.ACCEPTED, other.ref().tell(new LifeGame.ToggleCell(1, 1)));
        fixture.kit.runAll();
        assertTrue(second.messages().getLast().board().isAlive(1 + Board.WIDTH));
        fixture.actors.stop();
    }

    private static HttpRequest request(HttpMethod method, String path) {
        return new HttpRequest(method, path, path, URI.create("http://localhost" + path),
                "http://localhost" + path, Path.parse(path), Query.EMPTY,
                HttpHeaders.EMPTY, RequestBody.EMPTY);
    }

    private static String read(HttpResponse response) {
        try (var stream = response.body().openStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class Fixture {
        private static final QualifiedSessionId SESSION = new QualifiedSessionId("device", "one");
        private final ActorTestKit kit = new ActorTestKit();
        private final LocalActorSystem actors = LocalActorSystem.builder()
                .executor(kit.executor()).scheduler(kit.scheduler())
                .register(LifeGame.definition(new Random(42)))
                .build();
        private final PageActorDirectory<Long, LifeGame.Command> games = PageActorDirectory.numbered(
                actors, LifeGame.TYPE, LifeGame.Close::new);
        private final PageScope scope = new PageScope();
        private final PageActorDirectory.Entry<Long, LifeGame.Command> sessionGame = games.forPage(SESSION, scope);
        private final ActorRef<LifeGame.Command> game = sessionGame.ref();

        private Fixture() {
            actors.start();
        }
    }

    private static final class RecordingUpdater implements StateUpdater<State> {
        private final Queue<UnaryOperator<State>> work = new ArrayDeque<>();
        private State state = State.loading();

        @Override
        public void setState(State next) {
            work.add(_ -> next);
        }

        @Override
        public void applyStateTransformation(UnaryOperator<State> transformation) {
            work.add(transformation);
        }

        @Override
        public void applyStateTransformationIfPresent(Function<State, Optional<State>> transformation) {
            work.add(current -> transformation.apply(current).orElse(current));
        }

        void runAll() {
            while (!work.isEmpty()) {
                state = work.remove().apply(state);
            }
        }
    }
}
