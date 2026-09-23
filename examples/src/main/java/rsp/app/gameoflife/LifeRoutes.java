package rsp.app.gameoflife;

import rsp.actor.ActorAskTimeoutException;
import rsp.actor.ActorDeliveryException;
import rsp.actor.ActorGateway;
import rsp.actor.ActorRef;
import rsp.actor.SendResult;
import rsp.actor.http.ActorRouteHandler;
import rsp.actor.ui.PageActorDirectory;
import rsp.http.HttpStatus;
import rsp.http.json.JsonHttp;
import rsp.http.rest.RestException;
import rsp.http.rest.RestRouteHandler;
import rsp.http.routing.HttpRouteContext;
import rsp.http.routing.HttpRouter;
import rsp.util.json.Json;
import rsp.util.json.JsonDataType;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Active page games plus actor-backed status and control routes. */
final class LifeRoutes {
    private static final Duration DEADLINE = Duration.ofSeconds(2);

    private LifeRoutes() {
    }

    static HttpRouter router(ActorGateway actors, PageActorDirectory<Long, LifeGame.Command> games) {
        return HttpRouter.builder()
                .get("/api/games", RestRouteHandler.async((_, _) -> {
                    List<CompletableFuture<Optional<LifeGame.GameSummary>>> summaries = games.all().stream()
                            .map(game -> summary(actors, games, game))
                            .toList();
                    return CompletableFuture.allOf(summaries.toArray(CompletableFuture[]::new))
                            .thenApply(_ -> JsonHttp.response(Json.array(summaries.stream()
                                    .flatMap(future -> future.join().stream())
                                    .map(LifeRoutes::json)
                                    .toArray(JsonDataType[]::new))));
                }))
                .get("/api/games/{id}", ActorRouteHandler.<LifeGame.Command, LifeGame.GameSummary>ask(
                        actors, (_, route) -> game(games, route),
                        (_, _, replyTo) -> new LifeGame.Status(replyTo), DEADLINE,
                        summary -> JsonHttp.response(json(summary))))
                .post("/api/games/{id}/start", control(actors, games, LifeGame.Action.START))
                .post("/api/games/{id}/pause", control(actors, games, LifeGame.Action.PAUSE))
                .post("/api/games/{id}/reset", control(actors, games, LifeGame.Action.RESET))
                .build();
    }

    private static CompletableFuture<Optional<LifeGame.GameSummary>> summary(
            ActorGateway actors, PageActorDirectory<Long, LifeGame.Command> games,
            PageActorDirectory.Entry<Long, LifeGame.Command> game) {
        return actors.<LifeGame.Command, LifeGame.GameSummary>ask(
                game.ref(), LifeGame.Status::new, DEADLINE).handle((value, failure) -> {
                    if (failure == null) {
                        return Optional.of(value);
                    }
                    Throwable cause = failure instanceof CompletionException completion
                            ? completion.getCause() : failure;
                    if (cause instanceof ActorDeliveryException rejected
                            && rejected.result() == SendResult.STOPPED
                            && games.find(game.id()).isEmpty()) {
                        return Optional.<LifeGame.GameSummary>empty();
                    }
                    if (cause instanceof ActorDeliveryException rejected
                            && (rejected.result() == SendResult.STOPPED
                            || rejected.result() == SendResult.MAILBOX_FULL
                            || rejected.result() == SendResult.NOT_STARTED)) {
                        throw new RestException(HttpStatus.SERVICE_UNAVAILABLE, "actor_unavailable",
                                "The game is temporarily unavailable", rejected);
                    }
                    if (cause instanceof ActorAskTimeoutException timeout) {
                        throw new RestException(HttpStatus.GATEWAY_TIMEOUT, "actor_timeout",
                                "The game did not respond in time", timeout);
                    }
                    throw new CompletionException(cause);
                }).toCompletableFuture();
    }

    private static RestRouteHandler control(ActorGateway actors,
                                            PageActorDirectory<Long, LifeGame.Command> games,
                                            LifeGame.Action action) {
        return ActorRouteHandler.<LifeGame.Command, LifeGame.GameSummary>ask(
                actors, (_, route) -> game(games, route),
                (_, _, replyTo) -> LifeGame.Control.replying(action, replyTo),
                DEADLINE, summary -> JsonHttp.response(json(summary)));
    }

    private static ActorRef<LifeGame.Command> game(PageActorDirectory<Long, LifeGame.Command> games,
                                                    HttpRouteContext route) {
        String rawId = route.requiredParameter("id");
        long id;
        try {
            id = Long.parseLong(rawId);
        } catch (NumberFormatException invalid) {
            throw RestException.notFound("game_not_found", "Unknown game: " + rawId);
        }
        return games.find(id).orElseThrow(() ->
                RestException.notFound("game_not_found", "Unknown game: " + rawId)).ref();
    }

    private static JsonDataType.Object json(LifeGame.GameSummary summary) {
        return Json.object().put("id", summary.id())
                .put("kind", summary.kind())
                .put("status", summary.status().name())
                .put("generation", summary.generation());
    }
}
