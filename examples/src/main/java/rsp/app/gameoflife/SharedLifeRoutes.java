package rsp.app.gameoflife;

import rsp.actor.ActorAskTimeoutException;
import rsp.actor.ActorDeliveryException;
import rsp.actor.runtime.ActorSnapshot;
import rsp.actor.ui.ActorBinding;
import rsp.actor.ui.ActorRenderPolicy;
import rsp.http.AcceptNegotiation;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.HttpResult;
import rsp.http.HttpRouter;
import rsp.http.HttpStatus;
import rsp.http.PageResult;
import rsp.http.json.JsonHttp;
import rsp.http.rest.RestException;
import rsp.http.rest.RestRouteHandler;
import rsp.util.json.Json;
import rsp.util.json.JsonDataType;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.IntStream;

/** HTTP facade over application-owned simulations; item URLs also attach UI pages. */
final class SharedLifeRoutes {
    private static final String HTML = "text/html; charset=utf-8";
    private static final String JSON = "application/json; charset=utf-8";

    private SharedLifeRoutes() { }

    static HttpRouter router(Simulations simulations, ActorRenderPolicy rendering) {
        var routes = HttpRouter.builder()
                .postAsync("/games/shared", RestRouteHandler.async((_, _) -> respond(simulations.create(), created ->
                        JsonHttp.response(HttpStatus.CREATED, summary(created.snapshot().state().summary())
                                .put("url", url(created.id())))
                                .withHeader("Location", url(created.id())))))
                .getAsync("/games/shared", RestRouteHandler.sync((_, _) -> noStore(JsonHttp.response(
                        Json.array(simulations.list().stream().map(simulation ->
                                summary(simulation.snapshot().state().summary()).put("url", url(simulation.id())))
                                .toArray(JsonDataType[]::new))))))
                .get("/games/shared/{id}", (request, route) -> item(
                        simulations, rendering, request, route.requiredParameter("id")))
                .deleteAsync("/games/shared/{id}", RestRouteHandler.sync((_, route) -> {
                    try {
                        simulations.delete(id(route.requiredParameter("id")));
                        return noStore(HttpResponse.status(HttpStatus.NO_CONTENT).build());
                    } catch (Simulations.MissingSimulation missing) {
                        return failure(missing);
                    }
                }));
        for (LifeGame.Action action : new LifeGame.Action[]{LifeGame.Action.START, LifeGame.Action.PAUSE, LifeGame.Action.RESET}) {
            routes.postAsync("/games/shared/{id}/" + action.name().toLowerCase(java.util.Locale.ROOT),
                    RestRouteHandler.async((_, route) -> respond(
                            simulations.control(id(route.requiredParameter("id")), action),
                            value -> JsonHttp.response(summary(value)))));
        }
        return routes.build();
    }

    private static HttpResult item(Simulations simulations, ActorRenderPolicy rendering,
                                   HttpRequest request, String rawId) {
        final long id;
        try {
            id = Long.parseLong(rawId);
        } catch (NumberFormatException invalid) {
            return vary(missing());
        }
        var simulation = simulations.find(id);
        if (simulation.isEmpty()) return vary(missing());
        var representation = AcceptNegotiation.select(request.headers(), HTML, JSON);
        if (representation.isEmpty()) {
            return vary(JsonHttp.error(HttpStatus.NOT_ACCEPTABLE, "not_acceptable",
                    "Available representations are text/html and application/json"));
        }
        if (representation.orElseThrow().equals(JSON)) {
            return vary(JsonHttp.response(snapshot(simulation.orElseThrow().snapshot())));
        }
        return PageResult.live(new LifeComponent(ActorBinding.existing(simulation.orElseThrow().view()), rendering))
                .header("Vary", "Accept").header("Cache-Control", "no-store");
    }

    private static <T> CompletionStage<HttpResponse> respond(CompletionStage<T> result,
                                                             Function<T, HttpResponse> response) {
        return result.handle((value, failure) -> failure == null
                ? noStore(response.apply(value)) : failure(failure));
    }

    private static HttpResponse failure(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof Simulations.MissingSimulation) return missing();
        if (cause instanceof Simulations.CapacityExceeded) return noStore(JsonHttp.error(
                HttpStatus.SERVICE_UNAVAILABLE, "simulation_capacity", "Shared simulation capacity reached"));
        if (cause instanceof ActorDeliveryException) return noStore(JsonHttp.error(
                HttpStatus.SERVICE_UNAVAILABLE, "actor_unavailable", "The simulation is unavailable"));
        if (cause instanceof ActorAskTimeoutException) return noStore(JsonHttp.error(
                HttpStatus.GATEWAY_TIMEOUT, "actor_timeout", "The simulation did not finish before the deadline"));
        throw new CompletionException(cause);
    }

    private static long id(String raw) {
        try { return Long.parseLong(raw); }
        catch (NumberFormatException invalid) {
            throw RestException.notFound("game_not_found", "Unknown simulation");
        }
    }

    private static String url(long id) { return "/games/shared/" + id; }

    private static HttpResponse missing() {
        return noStore(JsonHttp.error(HttpStatus.NOT_FOUND, "game_not_found", "Unknown simulation"));
    }

    private static HttpResponse noStore(HttpResponse response) {
        return response.withHeader("Cache-Control", "no-store");
    }

    private static HttpResponse vary(HttpResponse response) {
        return noStore(response).withHeader("Vary", "Accept");
    }

    private static JsonDataType.Object summary(LifeGame.GameSummary summary) {
        return Json.object().put("id", summary.id()).put("kind", summary.kind())
                .put("status", summary.status().name()).put("generation", summary.generation());
    }

    private static JsonDataType.Object snapshot(ActorSnapshot<LifeGame.State> snapshot) {
        var state = snapshot.state();
        var cells = Json.array(IntStream.range(0, state.board().size()).filter(state.board()::isAlive)
                .mapToObj(index -> Json.object().put("x", Board.x(index)).put("y", Board.y(index)))
                .toArray(JsonDataType[]::new));
        return summary(state.summary()).put("revision", snapshot.revision())
                .put("lifecycle", snapshot.status().name()).put("board", Json.object()
                        .put("width", Board.WIDTH).put("height", Board.HEIGHT).put("liveCells", cells));
    }
}
