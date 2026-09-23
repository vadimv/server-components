package rsp.app.gameoflife;

import org.junit.jupiter.api.Test;
import rsp.actor.ActorRef;
import rsp.actor.SendResult;
import rsp.actor.runtime.LocalActorSystem;
import rsp.actor.testkit.ActorProbe;
import rsp.actor.testkit.ActorTestKit;
import rsp.actor.ui.PageActorDirectory;
import rsp.actor.ui.PageActorHandle;
import rsp.actor.ui.PageActorRuntime;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.dom.DomEventEntry;
import rsp.dom.NodeId;
import rsp.dom.TreePositionPath;
import rsp.http.HttpHeaders;
import rsp.http.HttpMethod;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;
import rsp.http.RequestBody;
import rsp.page.EventContext;
import rsp.page.PageBuilder;
import rsp.page.PageScope;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;
import rsp.page.events.GenericTaskEvent;
import rsp.url.Path;
import rsp.url.Query;
import rsp.util.json.Json;
import rsp.util.json.JsonDataType;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeGameTests {
    @Test
    void boardAdvancesWithoutMutatingPublishedState() {
        Board before = Board.empty().toggle(1, 1).toggle(2, 1).toggle(3, 1);
        Board after = before.advance();
        assertTrue(before.isAlive(1 + Board.WIDTH));
        assertFalse(after.isAlive(1 + Board.WIDTH));
        assertTrue(after.isAlive(2));
        assertTrue(after.isAlive(2 + Board.WIDTH));
        assertTrue(after.isAlive(2 + Board.WIDTH * 2));
    }

    @Test
    void sameDefinitionRunsInLocalHostWithIdempotentControlsAndInvalidatedTicks() {
        ActorTestKit kit = new ActorTestKit();
        LocalActorSystem actors = LocalActorSystem.builder()
                .executor(kit.executor()).scheduler(kit.scheduler())
                .register(LifeGame.definition(new Random(42)))
                .build();
        actors.start();
        try {
            ActorRef<LifeGame.Command> game = actors.ref(LifeGame.TYPE.id(17L));
            ActorProbe<LifeGame.GameSummary> replies = kit.probe();
            game.tell(LifeGame.Control.replying(LifeGame.Action.START, replies));
            kit.runAll();
            assertEquals(17L, replies.messages().getLast().id());
            assertEquals(LifeGame.Phase.RUNNING, replies.messages().getLast().status());
            assertEquals(1, kit.scheduler().pendingCount());

            game.tell(LifeGame.Control.of(LifeGame.Action.START));
            kit.runAll();
            assertEquals(1, kit.scheduler().pendingCount());

            game.tell(LifeGame.Control.of(LifeGame.Action.PAUSE));
            game.tell(LifeGame.Control.of(LifeGame.Action.START));
            kit.runAll();
            kit.advance(LifeGame.TICK_INTERVAL);
            game.tell(new LifeGame.Status(replies));
            kit.runAll();
            assertEquals(1, replies.messages().getLast().generation());

            game.tell(LifeGame.Control.of(LifeGame.Action.RESET));
            kit.runAll();
            kit.advance(LifeGame.TICK_INTERVAL.multipliedBy(3));
            game.tell(new LifeGame.Status(replies));
            kit.runAll();
            assertEquals(LifeGame.Phase.READY, replies.messages().getLast().status());
            assertEquals(0, replies.messages().getLast().generation());
            assertEquals(0, kit.scheduler().pendingCount());
        } finally {
            actors.stop();
        }
    }

    @Test
    void statusAndControlRoutesUseThePageHostedActor() {
        try (PageFixture fixture = new PageFixture()) {
            PageActorHandle<LifeGame.State, LifeGame.Command> game = fixture.activate();
            var router = LifeRoutes.router(fixture.runtime, fixture.games);
            var catalog = router.handle(request(HttpMethod.GET, "/api/games"));
            fixture.commands.drain();
            HttpResponse listed = catalog.toCompletableFuture().join();
            JsonDataType.Array games = (JsonDataType.Array) Json.parse(read(listed));
            assertEquals(1, games.elements().length);
            assertEquals(1L, Json.requireObject(games.elements()[0])
                    .requiredNumber("id").asLong());

            HttpResponse unknown = router.handle(request(HttpMethod.GET, "/api/games/missing"))
                    .toCompletableFuture().join();
            assertEquals(HttpStatus.NOT_FOUND, unknown.status());
            assertEquals(HttpStatus.NOT_FOUND, router.handle(request(HttpMethod.GET,
                    "/api/games/999999999999999999999"))
                    .toCompletableFuture().join().status());

            var started = router.handle(request(HttpMethod.POST, "/api/games/1/start"));
            fixture.commands.drain();
            assertEquals("RUNNING", Json.requireObject(Json.parse(
                            read(started.toCompletableFuture().join())))
                    .requiredString("status"));
            assertEquals(LifeGame.Phase.RUNNING, game.state().summary().status());

            var paused = router.handle(request(HttpMethod.POST, "/api/games/1/pause"));
            fixture.commands.drain();
            assertEquals("PAUSED", Json.requireObject(Json.parse(
                            read(paused.toCompletableFuture().join())))
                    .requiredString("status"));

            fixture.games.close(PageFixture.PAGE);
            assertEquals(HttpStatus.NOT_FOUND, router.handle(request(
                    HttpMethod.GET, "/api/games/1")).toCompletableFuture().join().status());
            assertEquals(SendResult.STOPPED, game.ref().tell(new LifeGame.ToggleCell(1, 1)));
        }
    }

    @Test
    void actorComponentRendersInitialStateDispatchesCommandsAndOnlyDetachesOnUnmount() {
        try (PageFixture fixture = new PageFixture()) {
            LifeComponent component = new LifeComponent(fixture.games, fixture.definition);
            PageBuilder builder = fixture.builder();

            component.createComponentSegment(PageFixture.PAGE,
                    TreePositionPath.of("99"), builder,
                    fixture.context(), fixture.commands);
            assertTrue(fixture.games.all().isEmpty(),
                    "an unrendered reconciliation candidate must not activate an actor");

            component.render(builder);
            assertTrue(builder.html().contains("Game 1 · READY · generation 0"));
            assertFalse(builder.html().contains("Connecting"));
            PageActorHandle<LifeGame.State, LifeGame.Command> game = fixture.activate();

            DomEventEntry firstCell = builder.recursiveEvents().getFirst();
            firstCell.eventHandler.accept(event(firstCell.eventTarget.nodeId()));
            fixture.commands.drain();
            assertTrue(game.state().board().isAlive(0));
            assertTrue(builder.html().contains("class=\"c1\""));

            builder.shutdown();
            assertEquals(SendResult.ACCEPTED,
                    game.ref().tell(new LifeGame.ToggleCell(1, 0)));
            fixture.commands.drain();
            assertTrue(game.state().board().isAlive(1));

            PageBuilder remounted = fixture.builder();
            component.render(remounted);
            assertTrue(remounted.html().contains("Game 1 · READY · generation 0"));
            assertEquals(2, remounted.html().split("class=\"c1\"", -1).length - 1);
            remounted.shutdown();

            fixture.scope.close();
            assertEquals(SendResult.STOPPED,
                    game.ref().tell(new LifeGame.ToggleCell(2, 0)));
            assertTrue(fixture.games.all().isEmpty());
        }
    }

    @Test
    void separatePagesHaveSeparateIdsAndAuthoritativeStates() {
        PageActorRuntime runtime = PageActorRuntime.builder().build();
        runtime.start();
        try {
            var definition = LifeGame.definition(new Random(42));
            var games = PageActorDirectory.numbered(runtime, LifeGame.TYPE);
            TestCommands firstCommands = new TestCommands();
            TestCommands secondCommands = new TestCommands();
            PageScope firstScope = new PageScope();
            PageScope secondScope = new PageScope();
            QualifiedSessionId firstPage = new QualifiedSessionId("device", "one");
            QualifiedSessionId secondPage = new QualifiedSessionId("device", "two");
            var first = games.activate(firstPage, firstScope, firstCommands, definition);
            var second = games.activate(secondPage, secondScope, secondCommands, definition);

            assertNotEquals(first.id(), second.id());
            first.ref().tell(new LifeGame.ToggleCell(2, 3));
            firstCommands.drain();
            assertTrue(first.state().board().isAlive(3 * Board.WIDTH + 2));
            assertFalse(second.state().board().isAlive(3 * Board.WIDTH + 2));

            firstScope.close();
            assertEquals(SendResult.STOPPED,
                    first.ref().tell(new LifeGame.ToggleCell(1, 1)));
            assertEquals(SendResult.ACCEPTED,
                    second.ref().tell(new LifeGame.ToggleCell(1, 1)));
            secondCommands.drain();
            assertTrue(second.state().board().isAlive(1 + Board.WIDTH));
            secondScope.close();
        } finally {
            runtime.stop();
        }
    }

    private static EventContext event(NodeId nodeId) {
        return new EventContext(nodeId,
                _ -> CompletableFuture.completedFuture(JsonDataType.Object.EMPTY),
                _ -> null, JsonDataType.Object.EMPTY, (_, _) -> { }, _ -> { });
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

    private static final class PageFixture implements AutoCloseable {
        private static final QualifiedSessionId PAGE =
                new QualifiedSessionId("device", "one");
        private final ActorTestKit kit = new ActorTestKit();
        private final PageActorRuntime runtime = PageActorRuntime.builder()
                .scheduler(kit.scheduler()).build();
        private final rsp.actor.ActorDefinition<LifeGame.State, LifeGame.Command> definition =
                LifeGame.definition(new Random(42));
        private final PageActorDirectory<Long, LifeGame.Command> games =
                PageActorDirectory.numbered(runtime, LifeGame.TYPE);
        private final PageScope scope = new PageScope();
        private final TestCommands commands = new TestCommands();

        private PageFixture() {
            runtime.start();
        }

        private PageActorHandle<LifeGame.State, LifeGame.Command> activate() {
            return games.activate(PAGE, scope, commands, definition);
        }

        private PageBuilder builder() {
            return new PageBuilder(PAGE, Optional.empty(), context(), commands);
        }

        private ComponentContext context() {
            return new ComponentContext()
                    .with(PageScope.class, scope)
                    .with(CommandsEnqueue.class, commands);
        }

        @Override
        public void close() {
            scope.close();
            runtime.stop();
        }
    }

    private static final class TestCommands implements CommandsEnqueue {
        private final Queue<Command> pending = new ArrayDeque<>();
        private final List<Command> output = new ArrayList<>();

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
                    output.add(command);
                }
            }
        }
    }
}
