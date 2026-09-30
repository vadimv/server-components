package rsp.app.gameoflife;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import java.util.Random;
import rsp.util.json.Json;
import rsp.util.json.JsonDataType;
import com.microsoft.playwright.options.RequestOptions;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Optional browser check for the actor-to-component event bridge. */
class LifeSmokeIT {
    @Test
    void delayedFirstConnectionReplaysChangesToTheSameGame() {
        try (var server = Life.server(0, new Random(42));
             Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch();
             BrowserContext context = browser.newContext()) {
            server.start();
            // Delay the client's DOMContentLoaded initializer while allowing HTML
            // rendering and HTTP requests to finish normally.
            context.addInitScript("""
                    const add = document.addEventListener;
                    document.addEventListener = function(type, listener, options) {
                      if (type === 'DOMContentLoaded') {
                        window.startRsp = () => listener.call(document, new Event(type));
                        document.addEventListener = add;
                      } else {
                        return add.call(this, type, listener, options);
                      }
                    };
                    """);
            Page page = context.newPage();
            assertEquals(200, page.navigate("http://127.0.0.1:" + server.port() + "/").status());
            String id = page.locator(".game > p").first().innerText().split(" ")[1];
            assertEquals("RUNNING", page.evaluate("""
                    id => fetch('/api/games/' + id + '/start', {method: 'POST'})
                      .then(response => response.json()).then(body => body.status)
                    """, id));
            assertEquals("PAUSED", page.evaluate("""
                    id => fetch('/api/games/' + id + '/pause', {method: 'POST'})
                      .then(response => response.json()).then(body => body.status)
                    """, id));
            assertThat(page.locator(".game > p").first()).containsText("Game " + id + " · READY");

            page.evaluate("() => window.startRsp()");
            assertThat(page.locator(".game > p").first()).containsText("Game " + id + " · PAUSED");
            page.locator(".board > div").first().click();
            assertThat(page.locator(".board > div").first()).hasClass("c1");
            assertEquals(id, page.locator(".game > p").first().innerText().split(" ")[1]);
        }
    }

    @Test
    void twoPagesHaveIndependentGamesAndControls() {
        try (var server = Life.server(0, new Random(42));
             Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch();
             BrowserContext context = browser.newContext()) {
            server.start();
            String url = "http://127.0.0.1:" + server.port() + "/";
            var first = context.newPage();
            var second = context.newPage();
            assertEquals(200, first.navigate(url).status());
            assertEquals(200, second.navigate(url).status());
            assertThat(first.locator(".board > div")).hasCount(Board.WIDTH * Board.HEIGHT);
            assertThat(second.locator(".board > div")).hasCount(Board.WIDTH * Board.HEIGHT);

            long firstId = Long.parseLong(first.locator(".game > p").first()
                    .innerText().split(" ")[1]);
            Object started = first.evaluate("id => fetch('/api/games/' + id + '/start', "
                    + "{method: 'POST'}).then(response => response.json()).then(body => body.status)",
                    Long.toString(firstId));
            assertEquals("RUNNING", started);
            assertThat(first.locator(".game > p").first()).containsText("RUNNING");
            assertThat(second.locator(".game > p").first()).containsText("READY");
            first.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new com.microsoft.playwright.Page.GetByRoleOptions().setName("Pause")).click();
            assertThat(first.locator(".game > p").first()).containsText("PAUSED");
            first.locator(".board > div").first().click();
            assertThat(first.locator(".board > div").first()).hasClass("c1");
            assertThat(second.locator(".board > div").first()).hasClass("c0");

            first.evaluate("() => window.RSP.disconnect()");
            first.waitForFunction("id => fetch('/api/games/' + id).then(response => response.status === 404)",
                    Long.toString(firstId), new Page.WaitForFunctionOptions().setTimeout(5000));
            assertThat(second.locator(".game > p").first()).containsText("READY");
        }
    }

    @Test
    void sharedViewersControlOneSimulationAndItSurvivesAllViewersClosing() {
        try (var server = Life.server(0, new Random(42));
             Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch();
             BrowserContext context = browser.newContext()) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();
            var created = context.request().post(base + "/games/shared");
            assertEquals(201, created.status());
            String url = base + Json.requireObject(Json.parse(created.text())).requiredString("url");
            var first = context.newPage();
            var second = context.newPage();
            assertEquals(200, first.navigate(url).status());
            assertEquals(200, second.navigate(url).status());
            first.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Pause")).click();
            assertThat(first.locator(".game > p")).containsText("PAUSED");
            assertThat(second.locator(".game > p")).containsText("PAUSED");
            first.locator(".board > div").first().click();
            assertThat(first.locator(".board > div").first()).hasClass("c1");
            assertThat(second.locator(".board > div").first()).hasClass("c1");
            var paused = sharedSnapshot(context, url);
            long generation = paused.requiredNumber("generation").asLong();
            assertEquals(1, paused.requiredObject("board").requiredArray("liveCells").elements().length);

            first.evaluate("() => window.RSP.disconnect()");
            first.close();
            assertEquals(200, context.request().post(url + "/start").status());
            assertThat(second.locator(".game > p")).containsText("RUNNING");
            second.evaluate("() => window.RSP.disconnect()");
            second.close();
            generation = sharedSnapshot(context, url).requiredNumber("generation").asLong();
            // The poll uses the HTTP facade with no page attached.
            com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(context.request().get(url,
                    RequestOptions.create().setHeader("Accept", "application/json"))).isOK();
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
            while (sharedSnapshot(context, url).requiredNumber("generation").asLong() <= generation
                    && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            org.junit.jupiter.api.Assertions.assertTrue(sharedSnapshot(context, url).requiredNumber("generation").asLong() > generation);
            var later = context.newPage();
            assertEquals(200, later.navigate(url).status());
            assertThat(later.locator(".game > p")).containsText("RUNNING");
        }
    }

    @Test
    void deletingSharedSimulationKeepsFinalBoardAndDisablesAttachedControls() {
        try (var server = Life.server(0, new Random(42));
             Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch();
             BrowserContext context = browser.newContext()) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();
            String url = base + Json.requireObject(Json.parse(context.request().post(base + "/games/shared").text())).requiredString("url");
            context.request().post(url + "/pause");
            var first = context.newPage();
            var second = context.newPage();
            first.navigate(url);
            second.navigate(url);
            first.locator(".board > div").first().click();
            assertThat(second.locator(".board > div").first()).hasClass("c1");
            assertEquals(204, context.request().delete(url).status());
            for (Page page : new Page[]{first, second}) {
                assertThat(page.locator(".game > p")).containsText("STOPPED");
                assertThat(page.locator(".controls button:disabled")).hasCount(4);
                assertThat(page.locator(".board > div").first()).hasClass("c1");
            }
            assertEquals(404, context.request().get(url, RequestOptions.create().setHeader("Accept", "application/json")).status());
            assertEquals(404, context.request().post(url + "/start").status());
        }
    }

    @Test
    void sharedPageReplaysChangesMadeBeforeItsFirstWebSocketConnection() {
        try (var server = Life.server(0, new Random(42));
             Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch();
             BrowserContext context = browser.newContext()) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();
            String url = base + Json.requireObject(Json.parse(context.request().post(base + "/games/shared").text())).requiredString("url");
            context.request().post(url + "/pause");
            context.addInitScript("""
                    const add = document.addEventListener;
                    document.addEventListener = function(type, listener, options) {
                      if (type === 'DOMContentLoaded') {
                        window.startRsp = () => listener.call(document, new Event(type));
                        document.addEventListener = add;
                      } else {
                        return add.call(this, type, listener, options);
                      }
                    };
                    """);
            var page = context.newPage();
            page.navigate(url);
            assertThat(page.locator(".game > p")).containsText("PAUSED");
            assertEquals(200, context.request().post(url + "/reset").status());
            page.evaluate("() => window.startRsp()");
            assertThat(page.locator(".game > p")).containsText("READY");
        }
    }

    private static JsonDataType.Object sharedSnapshot(BrowserContext context, String url) {
        var response = context.request().get(url, RequestOptions.create().setHeader("Accept", "application/json"));
        assertEquals(200, response.status());
        return Json.requireObject(Json.parse(response.text()));
    }

}
