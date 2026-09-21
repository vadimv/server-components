package rsp.app.gameoflife;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LifeServerTests {
    @Test
    void servesLivePageAndActorBackedGameRoutesOnOneServer() throws Exception {
        try (var server = Life.server(0, new Random(42));
             var client = HttpClient.newHttpClient()) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();

            HttpResponse<String> page = client.send(HttpRequest.newBuilder(URI.create(base + "/"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("Game of Life"));

            HttpResponse<String> games = client.send(HttpRequest.newBuilder(URI.create(base + "/api/games"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, games.statusCode());
            assertTrue(games.body().contains("life-demo"));

            HttpResponse<String> started = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/games/life-demo/start"))
                            .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, started.statusCode());
            assertTrue(started.body().contains("\"status\":\"RUNNING\""));
        }
    }
}
