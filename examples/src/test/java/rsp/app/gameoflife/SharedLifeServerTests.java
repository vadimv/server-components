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

import static org.junit.jupiter.api.Assertions.*;

class SharedLifeServerTests {
    @Test
    void creationAndJsonUseNoPageAndHtmlAttachesToTheExistingSimulation() throws Exception {
        try (var server = Life.server(0, new Random(42)); var client = HttpClient.newHttpClient()) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();
            var created = send(client, base, "/games/shared", "POST", null);
            assertEquals(201, created.statusCode());
            var body = object(created);
            long id = body.requiredNumber("id").asLong();
            String location = "/games/shared/" + id;
            assertEquals(location, created.headers().firstValue("Location").orElseThrow());
            assertEquals(location, body.requiredString("url"));
            assertEquals("RUNNING", body.requiredString("status"));
            assertTrue(server.pagesStorage.isEmpty());
            var json = send(client, base, location, "GET", "application/json");
            assertEquals(200, json.statusCode());
            assertEquals("Accept", json.headers().firstValue("Vary").orElseThrow());
            assertEquals("no-store", json.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(id, object(json).requiredNumber("id").asLong());
            assertNotNull(object(json).requiredObject("board"));
            assertTrue(server.pagesStorage.isEmpty());
            assertEquals(1, ((JsonDataType.Array) Json.parse(send(client, base, "/games/shared", "GET", null).body())).elements().length);
            assertEquals(0, ((JsonDataType.Array) Json.parse(send(client, base, "/api/games", "GET", null).body())).elements().length);
            for (int i = 1; i <= 2; i++) {
                var html = send(client, base, location, "GET", "text/html");
                assertEquals(200, html.statusCode());
                assertTrue(html.body().contains("Game " + id + " · RUNNING"));
                assertEquals("Accept", html.headers().firstValue("Vary").orElseThrow());
                assertTrue(html.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
                assertEquals(i, server.pagesStorage.size());
            }
            assertEquals("PAUSED", object(send(client, base, location + "/pause", "POST", null)).requiredString("status"));
            assertEquals("READY", object(send(client, base, location + "/reset", "POST", null)).requiredString("status"));
        }
    }

    @Test
    void contentNegotiationDefaultsToHtmlAndHonorsWeightsAndExclusions() throws Exception {
        try (var server = Life.server(0, new Random(42)); var client = HttpClient.newHttpClient()) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();
            String location = send(client, base, "/games/shared", "POST", null).headers().firstValue("Location").orElseThrow();
            assertTrue(send(client, base, location, "GET", null).headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
            int pages = server.pagesStorage.size();
            for (String accept : new String[]{"application/json", "text/html;q=0.2,application/json;q=0.8", "text/html;q=0,*/*;q=1"}) {
                var json = send(client, base, location, "GET", accept);
                assertEquals(200, json.statusCode());
                assertTrue(json.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
            }
            for (String accept : new String[]{"image/png", "text/html;q=0,application/json;q=0,*/*;q=1"}) {
                var rejected = send(client, base, location, "GET", accept);
                assertEquals(406, rejected.statusCode());
                assertEquals("Accept", rejected.headers().firstValue("Vary").orElseThrow());
            }
            assertEquals(pages, server.pagesStorage.size());
        }
    }

    @Test
    void capacityDeletionAndInvalidLookupsHaveNoImplicitCreation() throws Exception {
        try (var server = Life.server(0, new Random(42), 1); var client = HttpClient.newHttpClient()) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();
            for (String id : new String[]{"unknown", "999999999999999999999999", "-1", "1"}) {
                assertEquals(404, send(client, base, "/games/shared/" + id, "GET", "text/html").statusCode());
            }
            assertTrue(server.pagesStorage.isEmpty());
            var created = send(client, base, "/games/shared", "POST", null);
            String location = created.headers().firstValue("Location").orElseThrow();
            assertEquals(503, send(client, base, "/games/shared", "POST", null).statusCode());
            assertEquals(204, send(client, base, location, "DELETE", null).statusCode());
            assertEquals(404, send(client, base, location, "DELETE", null).statusCode());
            assertEquals(404, send(client, base, location, "GET", "application/json").statusCode());
            assertEquals(404, send(client, base, location + "/start", "POST", null).statusCode());
            var next = send(client, base, "/games/shared", "POST", null);
            assertEquals(201, next.statusCode());
            assertNotEquals(location, next.headers().firstValue("Location").orElseThrow());
        }
    }

    private static JsonDataType.Object object(HttpResponse<String> response) {
        return Json.requireObject(Json.parse(response.body()));
    }

    private static HttpResponse<String> send(HttpClient client, String base, String path, String method, String accept)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (accept != null) request.header("Accept", accept);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
