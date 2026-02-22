package rsp.app.posts;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rsp.jetty.WebServer;

import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E smoke tests for BasicAuthProvider (HTTP Basic authentication).
 */
@net.jcip.annotations.NotThreadSafe
class BasicAuthSmokeIT {

    private static final int PORT = 8082;
    private static final int PAGE_INIT_MS = 500;
    private static final String BASE_URL = "http://localhost:" + PORT;

    private static final Playwright playwright = Playwright.create();
    private static WebServer server;

    @BeforeAll
    static void init() {
        server = AuthTestApps.basicAuth(PORT);
    }

    @AfterAll
    static void shutdown() throws Exception {
        server.stop();
        Thread.sleep(2000);
    }

    @ParameterizedTest
    @MethodSource("browserTypes")
    void should_pass_basic_auth_smoke_tests(final BrowserType browserType) throws Exception {
        final Browser browser = browserType.launch();
        System.out.println("BasicAuth - Browser: " + browserType.name());

        validateUnauthenticatedGets401(browser);
        validateLoginWithCredentials(browser);
        validateWrongCredentials(browser);
    }

    // 1. Unauthenticated request gets 401
    private void validateUnauthenticatedGets401(Browser browser) throws Exception {
        System.out.println("Testing: Unauthenticated gets 401");
        final BrowserContext context = browser.newContext();
        final Page page = context.newPage();

        Response response = page.navigate(BASE_URL + "/posts");
        assertNotNull(response);
        assertEquals(401, response.status(), "Should get 401, but got: " + response.status());

        context.close();
        System.out.println("✓ 401 response validated");
    }

    // 2. Correct credentials → authenticated, header shows username, no sign-out button
    private void validateLoginWithCredentials(Browser browser) throws Exception {
        System.out.println("Testing: Login with correct credentials");
        final BrowserContext context = browser.newContext(
                new Browser.NewContextOptions().setHttpCredentials("admin", "pass123"));
        final Page page = context.newPage();

        page.navigate(BASE_URL + "/posts");
        waitFor(PAGE_INIT_MS);

        // Should be on posts page
        assertTrue(page.url().contains("/posts"), "Should be on /posts, but URL is: " + page.url());

        // Verify header shows username
        assertThat(page.locator(".header-username")).isVisible();
        assertThat(page.locator(".header-username")).containsText("admin");

        // Sign-out button should NOT be visible (BasicAuth doesn't support sign-out)
        assertEquals(0, page.locator(".header-signout").count(),
                "Sign-out button should not be present for Basic auth");

        context.close();
        System.out.println("✓ Login with credentials validated");
    }

    // 3. Wrong credentials → stays 401
    private void validateWrongCredentials(Browser browser) throws Exception {
        System.out.println("Testing: Wrong credentials");
        final BrowserContext context = browser.newContext(
                new Browser.NewContextOptions().setHttpCredentials("admin", "wrong"));
        final Page page = context.newPage();

        Response response = page.navigate(BASE_URL + "/posts");
        assertNotNull(response);
        assertEquals(401, response.status(), "Wrong password should still get 401, but got: " + response.status());

        // Username should not be visible
        assertEquals(0, page.locator(".header-username").count(),
                "Username should not be visible with wrong credentials");

        context.close();
        System.out.println("✓ Wrong credentials validated");
    }

    private static Stream<BrowserType> browserTypes() {
        return Stream.of(playwright.chromium());
    }

    private static void waitFor(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }
}
