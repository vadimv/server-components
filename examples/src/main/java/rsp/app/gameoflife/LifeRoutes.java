package rsp.app.gameoflife;

import rsp.actor.ActorRef;
import rsp.actor.ActorSystem;
import rsp.actor.http.ActorRouteHandler;
import rsp.http.json.JsonHttp;
import rsp.http.rest.RestException;
import rsp.http.routing.HttpRouteContext;
import rsp.http.routing.HttpRouter;
import rsp.util.json.Json;
import rsp.util.json.JsonDataType;

import java.time.Duration;

/** Fixed game catalog plus actor-backed status and control routes. */
final class LifeRoutes {
    private static final Duration DEADLINE = Duration.ofSeconds(2);

    private LifeRoutes() {
    }

    static HttpRouter router(ActorSystem actors) {
        return HttpRouter.builder()
                .get("/api/games", ActorRouteHandler.<LifeGame.Command, LifeGame.GameSummary>ask(
                        actors, (_, _) -> actors.ref(LifeGame.TYPE, LifeGame.ID),
                        (_, _, replyTo) -> new LifeGame.Status(replyTo), DEADLINE,
                        summary -> JsonHttp.response(Json.array(json(summary)))))
                .get("/api/games/{id}", ActorRouteHandler.<LifeGame.Command, LifeGame.GameSummary>ask(
                        actors, (_, route) -> game(actors, route),
                        (_, _, replyTo) -> new LifeGame.Status(replyTo), DEADLINE,
                        summary -> JsonHttp.response(json(summary))))
                .post("/api/games/{id}/start", control(actors, LifeGame.Action.START))
                .post("/api/games/{id}/pause", control(actors, LifeGame.Action.PAUSE))
                .post("/api/games/{id}/reset", control(actors, LifeGame.Action.RESET))
                .build();
    }

    private static rsp.http.rest.RestRouteHandler control(ActorSystem actors, LifeGame.Action action) {
        return ActorRouteHandler.<LifeGame.Command, LifeGame.GameSummary>ask(
                actors, (_, route) -> game(actors, route),
                (_, _, replyTo) -> LifeGame.Control.replying(action, replyTo),
                DEADLINE, summary -> JsonHttp.response(json(summary)));
    }

    private static ActorRef<LifeGame.Command> game(ActorSystem actors, HttpRouteContext route) {
        String id = route.requiredParameter("id");
        if (!id.equals(LifeGame.ID)) {
            throw RestException.notFound("game_not_found", "Unknown game: " + id);
        }
        return actors.ref(LifeGame.TYPE, id);
    }

    private static JsonDataType.Object json(LifeGame.GameSummary summary) {
        return Json.object().put("id", summary.id())
                .put("kind", summary.kind())
                .put("status", summary.status().name())
                .put("generation", summary.generation());
    }
}
