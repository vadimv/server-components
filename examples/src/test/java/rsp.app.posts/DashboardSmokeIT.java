package rsp.app.posts;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rsp.app.posts.components.DashboardModel;
import rsp.jetty.WebServer;

import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@net.jcip.annotations.NotThreadSafe
class DashboardSmokeIT {

    private static final int PORT = 8085;
    private static final int EXPECTED_PAGE_INIT_TIME_MS = 300;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final String EXPECTED_COMMENTS_RATE_VALUE = String.valueOf(
            DashboardModel.demo()
                    .layout()
                    .placements()
                    .getFirst()
                    .widget()
                    .metadataState()
                    .get("currentValue"));

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
        assertThat(primaryScope(page).locator(".dashboard-widget-value"))
                .containsText(EXPECTED_COMMENTS_RATE_VALUE);
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
