package rsp.app.gameoflife;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Optional browser check for the actor-to-component event bridge. */
class LifeSmokeIT {
    @Test
    void twoPagesObserveTheSameGameAndControls() {
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

            first.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new com.microsoft.playwright.Page.GetByRoleOptions().setName("Start")).click();
            assertThat(second.locator(".game > p").first()).containsText("RUNNING");
            second.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new com.microsoft.playwright.Page.GetByRoleOptions().setName("Pause")).click();
            assertThat(first.locator(".game > p").first()).containsText("PAUSED");
            first.locator(".board > div").first().click();
            assertThat(second.locator(".board > div").first()).hasClass("c1");
        }
    }
}
