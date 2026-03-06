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
 * E2E smoke tests for SimpleAuthProvider (cookie-based session auth).
 */
@net.jcip.annotations.NotThreadSafe
class SimpleAuthSmokeIT {

    private static final int PORT = 8081;
    private static final int PAGE_INIT_MS = 500;
    private static final String BASE_URL = "http://localhost:" + PORT;

    private static final Playwright playwright = Playwright.create();
    private static WebServer server;

    @BeforeAll
    static void init() {
        server = AuthTestApps.simpleAuth(PORT);
    }

    @AfterAll
    static void shutdown() throws Exception {
        server.stop();
        Thread.sleep(2000);
    }

    @ParameterizedTest
    @MethodSource("browserTypes")
    void should_pass_simple_auth_smoke_tests(final BrowserType browserType) throws Exception {
        final Browser browser = browserType.launch();
        System.out.println("SimpleAuth - Browser: " + browserType.name());

        validateProtectedRedirectsToLogin(browser);
        validateLoginAndHeader(browser);
        validateSignOut(browser);
        validateUnauthenticatedCannotAccessPosts(browser);
    }

    // 1. Protected page redirects to login
    private void validateProtectedRedirectsToLogin(Browser browser) throws Exception {
        System.out.println("Testing: Protected page redirects to login");
        final BrowserContext context = browser.newContext();
        final Page page = context.newPage();

        page.navigate(BASE_URL + "/posts");
        waitFor(PAGE_INIT_MS);

        // Should be on login page
        assertTrue(page.url().contains("/auth/login"), "Should redirect to login, but URL is: " + page.url());
        assertThat(page.locator("h1:has-text('Sign In')")).isVisible();

        context.close();
        System.out.println("✓ Protected redirect validated");
    }

    // 2. Login flow → header shows username + sign-out button
    private void validateLoginAndHeader(Browser browser) throws Exception {
        System.out.println("Testing: Login and header display");
        final BrowserContext context = browser.newContext();
        final Page page = context.newPage();

        // Navigate to protected page → redirected to login
        page.navigate(BASE_URL + "/posts");
        waitFor(PAGE_INIT_MS);
        assertTrue(page.url().contains("/auth/login"));

        // Click "Sign in" button
        page.locator("button:has-text('Sign in')").click();
        waitFor(PAGE_INIT_MS);

        // Should be redirected back to /posts
        page.waitForURL("**/posts**", new Page.WaitForURLOptions().setTimeout(5000));
        assertTrue(page.url().contains("/posts"), "Should be on /posts after login, but URL is: " + page.url());

        // Verify header shows username
        assertThat(page.locator(".header-username")).isVisible();
        assertThat(page.locator(".header-username")).containsText("admin");

        // Verify sign-out button is visible (SimpleAuth supports sign-out)
        assertThat(page.locator(".header-signout")).isVisible();

        context.close();
        System.out.println("✓ Login and header validated");
    }

    // 3. Sign-out → session cleared → re-navigating to /posts redirects to login
    private void validateSignOut(Browser browser) throws Exception {
        System.out.println("Testing: Sign out");
        final BrowserContext context = browser.newContext();
        final Page page = context.newPage();

        // Login first
        page.navigate(BASE_URL + "/posts");
        waitFor(PAGE_INIT_MS);
        page.locator("button:has-text('Sign in')").click();
        waitFor(PAGE_INIT_MS);
        page.waitForURL("**/posts**", new Page.WaitForURLOptions().setTimeout(5000));

        // Click sign-out — clears cookie + SetHref("/")
        page.locator(".header-signout").click();
        waitFor(2000);

        // Now navigate to /posts — session is cleared, should redirect to login
        page.navigate(BASE_URL + "/posts");
        waitFor(PAGE_INIT_MS);

        assertTrue(page.url().contains("/auth/login"),
                "Should redirect to login after sign-out, but URL is: " + page.url());
        assertThat(page.locator("h1:has-text('Sign In')")).isVisible();

        context.close();
        System.out.println("✓ Sign out validated");
    }

    // 4. Unauthenticated user cannot access posts
    private void validateUnauthenticatedCannotAccessPosts(Browser browser) throws Exception {
        System.out.println("Testing: Unauthenticated cannot access posts");
        final BrowserContext context = browser.newContext();
        final Page page = context.newPage();

        // Fresh context (no cookies) → should stay on login
        page.navigate(BASE_URL + "/posts");
        waitFor(PAGE_INIT_MS);

        assertTrue(page.url().contains("/auth/login"), "Should be on login page, but URL is: " + page.url());
        // Posts table should NOT be visible
        assertEquals(0, page.locator(".layout-primary table").count());

        context.close();
        System.out.println("✓ Unauthenticated access validated");
    }

    private static Stream<BrowserType> browserTypes() {
        return Stream.of(playwright.chromium());
    }

    private static void waitFor(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }
}
