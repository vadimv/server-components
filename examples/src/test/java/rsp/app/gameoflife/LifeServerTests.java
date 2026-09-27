package rsp.app.gameoflife;

import org.junit.jupiter.api.Test;
import rsp.util.json.Json;
import rsp.util.json.JsonDataType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Random;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class LifeServerTests {
    private static final Pattern GAME_ID = Pattern.compile("Game (\\d+) · READY");

    @Test
    void initialRenderCreatesOneNumericGamePerPage() throws Exception {
        try (var server = Life.server(0, new Random(42));
             var client = HttpClient.newHttpClient()) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();

            HttpResponse<String> page = client.send(HttpRequest.newBuilder(URI.create(base + "/"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("Game of Life"));
            assertTrue(page.body().contains("· READY ·"));

            HttpResponse<String> secondPage = client.send(HttpRequest.newBuilder(URI.create(base + "/"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, secondPage.statusCode());
            long firstId = gameId(page.body());
            long secondId = gameId(secondPage.body());
            assertNotEquals(firstId, secondId);
        }
    }

    @Test
    void httpOnlyClientCanListControlAndAdvanceItsGame() throws Exception {
        try (var server = Life.server(0, new Random(42));
             var client = HttpClient.newHttpClient()) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();

            HttpResponse<String> page = client.send(HttpRequest.newBuilder(URI.create(base + "/"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            long id = gameId(page.body());
            // An HTTP-only client never connects the page's WebSocket.
            HttpResponse<String> catalog = client.send(HttpRequest.newBuilder(URI.create(base + "/api/games"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, catalog.statusCode());
            var games = (JsonDataType.Array) Json.parse(catalog.body());
            assertEquals(1, games.elements().length);
            assertEquals(id, Json.requireObject(games.elements()[0]).requiredNumber("id").asLong());

            var started = control(client, base, id, "start");
            assertEquals("RUNNING", Json.requireObject(Json.parse(started.body())).requiredString("status"));
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            long generation;
            do {
                var status = client.send(HttpRequest.newBuilder(URI.create(base + "/api/games/" + id))
                        .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, status.statusCode());
                generation = Json.requireObject(Json.parse(status.body())).requiredNumber("generation").asLong();
                if (generation == 0) {
                    Thread.sleep(20);
                }
            } while (generation == 0 && System.nanoTime() < deadline);
            assertTrue(generation > 0, "game ticks must execute before WebSocket attachment");
            var paused = control(client, base, id, "pause");
            assertEquals("PAUSED", Json.requireObject(Json.parse(paused.body())).requiredString("status"));
        }
    }

    private static HttpResponse<String> control(HttpClient client, String base, long id, String action)
            throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(base + "/api/games/" + id + "/" + action))
                .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return response;
    }

    private static long gameId(String page) {
        return Long.parseLong(GAME_ID.matcher(page).results().findFirst()
                .orElseThrow().group(1));
    }
}
