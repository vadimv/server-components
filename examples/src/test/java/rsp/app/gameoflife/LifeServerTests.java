package rsp.app.gameoflife;

import org.junit.jupiter.api.Test;
import rsp.util.json.Json;
import rsp.util.json.JsonDataType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LifeServerTests {
    @Test
    void servesOneActorPerPageAndNumericGameRoutesOnOneServer() throws Exception {
        try (var server = Life.server(0, new Random(42));
             var client = HttpClient.newHttpClient()) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();

            HttpResponse<String> page = client.send(HttpRequest.newBuilder(URI.create(base + "/"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("Game of Life"));

            HttpResponse<String> secondPage = client.send(HttpRequest.newBuilder(URI.create(base + "/"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, secondPage.statusCode());

            HttpResponse<String> games = client.send(HttpRequest.newBuilder(URI.create(base + "/api/games"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, games.statusCode());
            JsonDataType.Array catalog = (JsonDataType.Array) Json.parse(games.body());
            assertEquals(2, catalog.elements().length);
            long firstId = Json.requireObject(catalog.elements()[0]).requiredNumber("id").asLong();
            long secondId = Json.requireObject(catalog.elements()[1]).requiredNumber("id").asLong();
            assertNotEquals(firstId, secondId);

            HttpResponse<String> started = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/games/" + firstId + "/start"))
                            .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, started.statusCode());
            assertTrue(started.body().contains("\"status\":\"RUNNING\""));

            HttpResponse<String> untouched = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/games/" + secondId))
                            .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, untouched.statusCode());
            assertTrue(untouched.body().contains("\"status\":\"READY\""));
        }
    }
}
