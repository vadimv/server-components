package rsp.app.posts;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rsp.jetty.WebServer;

import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@net.jcip.annotations.NotThreadSafe
class DashboardSmokeIT {

    private static final int PORT = 8085;
    private static final int EXPECTED_PAGE_INIT_TIME_MS = 300;
    private static final String BASE_URL = "http://localhost:" + PORT;

    private static final Playwright playwright = Playwright.create();
    private static WebServer server;

    @BeforeAll
    public static void init() {
        server = new CrudApp().run(false);
    }

    @AfterAll
    public static void shutdown() throws Exception {
        server.stop();
        Thread.sleep(2000);
    }

    @ParameterizedTest
    @MethodSource("browserTypes")
    void should_render_dashboard_from_explorer_navigation(final BrowserType browserType) throws Exception {
        final Browser browser = browserType.launch();
        final BrowserContext context = browser.newContext();
        final Page page = context.newPage();

        login(page);

        Locator dashboardLink = page.locator(".explorer-item > a:has-text(\"Dashboard\")");
        assertThat(dashboardLink).isVisible();
        dashboardLink.click();
        page.waitForURL(url -> url.contains("/dashboard"), new Page.WaitForURLOptions().setTimeout(5000));

        assertTrue(page.url().contains("/dashboard"),
                "Should navigate to /dashboard, but URL is: " + page.url());
        assertThat(primaryScope(page).locator("h1")).containsText("Dashboard");
        assertThat(primaryScope(page).locator(".dashboard-grid")).isVisible();
        assertThat(primaryScope(page).locator(".comments-rate-widget")).isVisible();
        assertThat(primaryScope(page).locator(".comments-rate-chart")).isVisible();
        assertThat(primaryScope(page).locator(".comments-rate-line")).isVisible();
        assertThat(primaryScope(page).locator(".dashboard-widget-value")).isVisible();
        assertThat(primaryScope(page).locator(".dashboard-widget-unit")).containsText("comments/sec");
        assertTrue((Boolean) primaryScope(page).locator(".comments-rate-line").evaluate("""
                line => line.namespaceURI === 'http://www.w3.org/2000/svg'
                        && line.getAttribute('points').trim().split(/\\s+/).length >= 2
                        && line.getBBox().width > 0
                        && line.getBBox().height >= 0
                """));
    }

    @ParameterizedTest
    @MethodSource("browserTypes")
    void should_preserve_keyed_log_row_identity_across_rotation(final BrowserType browserType) throws Exception {
        final Browser browser = browserType.launch();
        final BrowserContext context = browser.newContext();
        final Page page = context.newPage();

        login(page);
        page.locator(".explorer-item > a:has-text(\"Dashboard\")").click();
        page.waitForURL(url -> url.contains("/dashboard"), new Page.WaitForURLOptions().setTimeout(5000));

        final Locator rows = primaryScope(page).locator(".logs-row");
        assertThat(rows.first()).isVisible();

        // Tag the newest (bottom) row's DOM node and remember the message it shows.
        final Locator lastRow = rows.last();
        final String taggedMessage = (String) lastRow.evaluate("""
                el => { el.__keyedProbe = 'KEYED'; return el.querySelector('.logs-msg').textContent; }
                """);

        // Wait until a new line is appended (the bottom message changes), i.e. one rotation happened.
        assertThat(primaryScope(page).locator(".logs-row").last().locator(".logs-msg"))
                .not().hasText(taggedMessage,
                        new com.microsoft.playwright.assertions.LocatorAssertions.HasTextOptions().setTimeout(15000));

        // The row still showing the tagged message must be the SAME DOM node we tagged.
        // Under positional diffing the message would have been rewritten into a different node,
        // so the node carrying it would not have our probe.
        final Object probe = primaryScope(page).locator(".logs-row").evaluateAll("""
                (els, msg) => {
                    const row = els.find(e => e.querySelector('.logs-msg').textContent === msg);
                    return row ? (row.__keyedProbe || 'NO_PROBE') : 'ROW_GONE';
                }
                """, taggedMessage);
        assertEquals("KEYED", probe,
                "keyed row identity not preserved across rotation (probe=" + probe + ", msg=" + taggedMessage + ")");

        // And it should have scrolled up: no longer the bottom row.
        final String bottomMessageNow = primaryScope(page).locator(".logs-row").last()
                .locator(".logs-msg").textContent();
        assertNotEquals(taggedMessage, bottomMessageNow,
                "tagged row should have moved up after a new line was appended");
    }

    private static Stream<BrowserType> browserTypes() {
        return Stream.of(playwright.chromium()
                //      playwright.webkit(),
                //      playwright.firefox()
        );
    }

    private void login(final Page page) throws InterruptedException {
        page.navigate(BASE_URL + "/posts");
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        page.locator("button:has-text('Sign in')").click();
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        page.waitForURL("**/posts**", new Page.WaitForURLOptions().setTimeout(5000));
    }

    private Locator primaryScope(final Page page) {
        return page.locator(".layout-primary");
    }

    private static void waitFor(final long timeMs) throws InterruptedException {
        Thread.sleep(timeMs);
    }
}
