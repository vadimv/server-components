package rsp.actor.http;

import org.junit.jupiter.api.Test;
import rsp.application.ApplicationContext;
import rsp.actor.ActorBehavior;
import rsp.actor.ActorDefinition;
import rsp.actor.ActorEffect;
import rsp.actor.ActorEnvelope;
import rsp.actor.ActorRef;
import rsp.actor.ActorSystem;
import rsp.actor.ActorType;
import rsp.actor.MessageId;
import rsp.actor.runtime.LocalActorSystem;
import rsp.actor.testkit.ActorTestKit;
import rsp.http.HttpHeader;
import rsp.http.HttpApplication;
import rsp.http.HttpHeaders;
import rsp.http.HttpMethod;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;
import rsp.http.RequestBody;
import rsp.http.json.JsonHttp;
import rsp.http.rest.RestException;
import rsp.http.routing.HttpRouteMetadata;
import rsp.http.routing.HttpRouter;
import rsp.url.Path;
import rsp.url.Query;
import rsp.util.json.Json;
import rsp.util.json.JsonCodec;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class ActorRouteHandlerTests {
    private static final Duration DEADLINE = Duration.ofSeconds(2);
    private static final ActorType<String, Change> TYPE =
            ActorType.named("http-counter", Change.class, key -> key);
    private static final JsonCodec<Body> BODY_CODEC = JsonCodec.of(
            value -> new Body(Json.requireObject(value).requiredString("value")),
            body -> Json.object().put("value", body.value()));
    private static final JsonCodec<String> STRING_CODEC = JsonCodec.of(Json::requireString, Json::string);

    @Test
    void jsonRouteUsesPathKeyPreservesMessageIdAndKeepsRouteMetadata() {
        Fixture fixture = new Fixture(4);
        RouteInfo metadata = new RouteInfo("change counter");
        HttpRouter router = HttpRouter.builder()
                .post("/counters/{id}", ActorRouteHandler.jsonEnvelope(
                        fixture.actors, BODY_CODEC,
                        (_, route) -> fixture.actors.ref(TYPE, route.requiredParameter("id")),
                        (request, _, body, replyTo) -> new ActorEnvelope<>(
                                new MessageId(request.header("Idempotency-Key")),
                                new Change("ok", body.value(), replyTo), Optional.empty(), Optional.empty()),
                        DEADLINE, STRING_CODEC), metadata)
                .build();

        var response = router.handle(request(HttpMethod.POST, "/counters/cart-1",
                "{\"value\":\"hello\"}", "client-command-1"));
        fixture.kit.runAll();

        assertEquals(HttpStatus.OK, response.toCompletableFuture().join().status());
        assertEquals("\"hello\"", read(response.toCompletableFuture().join()));
        assertEquals(List.of("cart-1:client-command-1"), fixture.received);
        assertEquals(metadata, router.routeDefinitions().getFirst().metadata(RouteInfo.class).orElseThrow());
        fixture.actors.stop();
    }

    @Test
    void mapsDomainRejectionsButLetsUnexpectedActorFailuresReachServerBoundary() {
        Fixture fixture = new Fixture(4);
        HttpRouter router = HttpRouter.builder().get("/actors/{id}", ActorRouteHandler.<Change, String>ask(
                fixture.actors, (_, route) -> fixture.actors.ref(TYPE, route.requiredParameter("id")),
                (_, route, replyTo) -> new Change(route.requiredParameter("id"), "value", replyTo),
                DEADLINE, reply -> {
                    if (reply.equals("rejected")) {
                        throw RestException.conflict("counter_rejected", "Counter rejected the command");
                    }
                    return JsonHttp.response(reply, STRING_CODEC);
                })).build();

        var domain = router.handle(request(HttpMethod.GET, "/actors/reject", "", null));
        var failure = router.handle(request(HttpMethod.GET, "/actors/fail", "", null));
        fixture.kit.runAll();

        assertEquals(HttpStatus.CONFLICT, domain.toCompletableFuture().join().status());
        assertEquals("counter_rejected", errorCode(domain.toCompletableFuture().join()));
        assertInstanceOf(IllegalArgumentException.class, assertThrows(CompletionException.class,
                () -> failure.toCompletableFuture().join()).getCause());
        fixture.actors.stop();
    }

    @Test
    void mapsCapacityAndStoppingTo503AndOnlyAskDeadlinesTo504() {
        Fixture fixture = new Fixture(1);
        HttpRouter router = HttpRouter.builder().get("/actors/{id}", ActorRouteHandler.<Change, String>ask(
                fixture.actors, (_, route) -> fixture.actors.ref(TYPE, route.requiredParameter("id")),
                (_, route, replyTo) -> new Change(route.requiredParameter("id"), "value", replyTo),
                DEADLINE, reply -> JsonHttp.response(reply, STRING_CODEC))).build();

        var first = router.handle(request(HttpMethod.GET, "/actors/hang", "", null));
        var full = router.handle(request(HttpMethod.GET, "/actors/hang", "", null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, full.toCompletableFuture().join().status());
        assertEquals("actor_unavailable", errorCode(full.toCompletableFuture().join()));
        fixture.kit.runAll();
        fixture.kit.advance(DEADLINE);
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, first.toCompletableFuture().join().status());
        assertEquals("actor_timeout", errorCode(first.toCompletableFuture().join()));
        assertEquals(1, fixture.received.size());
        fixture.actors.stop();

        var stopped = router.handle(request(HttpMethod.GET, "/actors/other", "", null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, stopped.toCompletableFuture().join().status());
    }

    @Test
    void invalidJsonIsRejectedBeforeSendingAnActorCommand() {
        Fixture fixture = new Fixture(1);
        HttpRouter router = HttpRouter.builder().post("/actors/{id}", ActorRouteHandler.json(
                fixture.actors, BODY_CODEC,
                (_, route) -> fixture.actors.ref(TYPE, route.requiredParameter("id")),
                (_, _, body, replyTo) -> new Change("ok", body.value(), replyTo),
                DEADLINE, STRING_CODEC)).build();
        HttpResponse response = router.handle(request(HttpMethod.POST, "/actors/one", "{", null))
                .toCompletableFuture().join();
        assertEquals(HttpStatus.BAD_REQUEST, response.status());
        assertEquals("invalid_json", errorCode(response));
        assertTrue(fixture.received.isEmpty());
        fixture.actors.stop();
    }

    @Test
    void unansweredAskBecomes503WhenApplicationStopsBeforeItsDeadline() {
        Fixture fixture = new Fixture(1);
        HttpRouter router = HttpRouter.builder().get("/actors/{id}", ActorRouteHandler.<Change, String>ask(
                fixture.actors, (_, route) -> fixture.actors.ref(TYPE, route.requiredParameter("id")),
                (_, _, replyTo) -> new Change("hang", "", replyTo), DEADLINE,
                reply -> JsonHttp.response(reply, STRING_CODEC))).build();

        var answer = router.handle(request(HttpMethod.GET, "/actors/one", "", null));
        fixture.kit.runAll();
        fixture.actors.drainAndStop().toCompletableFuture().join();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, answer.toCompletableFuture().join().status());
        assertEquals(0, fixture.kit.scheduler().pendingCount());
    }

    @Test
    void applicationContextStartsAndStopsActorsBehindOrdinaryHttpRouter() {
        ActorTestKit kit = new ActorTestKit();
        LocalActorSystem actors = LocalActorSystem.builder()
                .executor(kit.executor()).scheduler(kit.scheduler())
                .register(ActorDefinition.<Integer, Change>builder(TYPE)
                        .initialState(_ -> 0)
                        .behavior(ActorBehavior.sync((_, state, message) ->
                                ActorEffect.<Integer>state(state + 1)
                                        .reply(message.replyTo(), message.value())))
                        .build()).build();
        ApplicationContext context = ApplicationContext.builder()
                .service(ActorSystem.class, actors).build();
        HttpRouter router = HttpRouter.builder().get("/actors/{id}", ActorRouteHandler.<Change, String>ask(
                actors, (_, route) -> actors.ref(TYPE, route.requiredParameter("id")),
                (_, _, replyTo) -> new Change("ok", "started", replyTo), DEADLINE,
                reply -> JsonHttp.response(reply, STRING_CODEC))).build();
        HttpApplication application = HttpApplication.withLifecycle(context, router);
        HttpRequest request = request(HttpMethod.GET, "/actors/one", "", null);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                application.handle(request).toCompletableFuture().join().status());
        application.start();
        var accepted = application.handle(request);
        kit.runAll();
        assertEquals("\"started\"", read(accepted.toCompletableFuture().join()));
        application.stop();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                application.handle(request).toCompletableFuture().join().status());
    }

    private static String errorCode(HttpResponse response) {
        return Json.requireObject(Json.parse(read(response))).requiredString("error");
    }

    private static String read(HttpResponse response) {
        try (var stream = response.body().openStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static HttpRequest request(HttpMethod method, String path, String body, String messageId) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpHeaders.Builder headers = HttpHeaders.builder();
        if (!body.isEmpty()) {
            headers.add(new HttpHeader("Content-Type", "application/json"));
        }
        if (messageId != null) {
            headers.add(new HttpHeader("Idempotency-Key", messageId));
        }
        return new HttpRequest(method, path, path, URI.create("http://localhost" + path),
                "http://localhost" + path, Path.parse(path), Query.EMPTY, headers.build(),
                RequestBody.of(bytes, 1024));
    }

    private record Body(String value) { }

    private record Change(String mode, String value, ActorRef<String> replyTo) { }

    private record RouteInfo(String title) implements HttpRouteMetadata { }

    private static final class Fixture {
        private final ActorTestKit kit = new ActorTestKit();
        private final List<String> received = new ArrayList<>();
        private final LocalActorSystem actors;

        private Fixture(int capacity) {
            actors = LocalActorSystem.builder().executor(kit.executor()).scheduler(kit.scheduler())
                    .register(ActorDefinition.<Integer, Change>builder(TYPE)
                            .initialState(_ -> 0)
                            .mailboxCapacity(capacity)
                            .behavior(ActorBehavior.sync((context, state, message) -> {
                                received.add(context.id().key() + ":" + context.envelope().messageId().value());
                                if (message.mode().equals("fail")) {
                                    throw new IllegalArgumentException("actor failed");
                                }
                                if (message.mode().equals("hang")) {
                                    return ActorEffect.state(state + 1);
                                }
                                return ActorEffect.<Integer>state(state + 1).reply(message.replyTo(),
                                        message.mode().equals("reject") ? "rejected" : message.value());
                            })).build()).build();
            actors.start();
        }
    }
}
