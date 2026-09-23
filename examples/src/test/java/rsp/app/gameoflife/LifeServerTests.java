package rsp.app.gameoflife;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    private static long gameId(String page) {
        return Long.parseLong(GAME_ID.matcher(page).results().findFirst()
                .orElseThrow().group(1));
    }
}
