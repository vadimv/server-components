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
 * E2E smoke tests for OAuthPKCEProvider with a StubOAuthServer.
 */
@net.jcip.annotations.NotThreadSafe
class OAuthPKCESmokeIT {

    private static final int PORT = 8083;
    private static final int OAUTH_PORT = 8084;
    private static final int PAGE_INIT_MS = 500;
    private static final String BASE_URL = "http://localhost:" + PORT;

    private static final Playwright playwright = Playwright.create();
    private static WebServer server;
    private static StubOAuthServer stubOAuth;

    @BeforeAll
    static void init() throws Exception {
        stubOAuth = new StubOAuthServer(OAUTH_PORT);
        stubOAuth.start();
        server = AuthTestApps.oauthPKCE(PORT, OAUTH_PORT);
    }

    @AfterAll
    static void shutdown() throws Exception {
        server.stop();
        stubOAuth.stop();
        Thread.sleep(2000);
    }

    @ParameterizedTest
    @MethodSource("browserTypes")
    void should_pass_oauth_pkce_smoke_tests(final BrowserType browserType) throws Exception {
        final Browser browser = browserType.launch();
        System.out.println("OAuthPKCE - Browser: " + browserType.name());

        validateProtectedRedirectsToLogin(browser);
        validateLoginAndHeader(browser);
        validateSignOut(browser);
        validateAuthorizationDenied(browser);
    }

    // 1. Protected page redirects to login
    private void validateProtectedRedirectsToLogin(Browser browser) throws Exception {
        System.out.println("Testing: Protected page redirects to OAuth login");
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

    // 2. Full PKCE flow: click sign-in → StubOAuthServer auto-approves → redirected to posts
    private void validateLoginAndHeader(Browser browser) throws Exception {
        System.out.println("Testing: OAuth PKCE login flow");
        final BrowserContext context = browser.newContext();
        final Page page = context.newPage();

        // Navigate to protected page → redirected to login
        page.navigate(BASE_URL + "/posts");
        waitFor(PAGE_INIT_MS);
        assertTrue(page.url().contains("/auth/login"));

        // Click "Sign in" → triggers PKCE flow → StubOAuthServer auto-approves → callback → /posts
        page.locator("button:has-text('Sign in')").click();

        // Wait for the full redirect chain to complete
        page.waitForURL("**/posts**", new Page.WaitForURLOptions().setTimeout(10000));
        waitFor(PAGE_INIT_MS);

        assertTrue(page.url().contains("/posts"), "Should be on /posts after OAuth login, but URL is: " + page.url());

        // Verify header shows username from StubOAuthServer
        assertThat(page.locator(".header-username")).isVisible();
        assertThat(page.locator(".header-username")).containsText("testuser");

        // Verify sign-out button is visible (OAuthPKCE supports sign-out)
        assertThat(page.locator(".header-signout")).isVisible();

        context.close();
        System.out.println("✓ OAuth login and header validated");
    }

    // 3. Sign-out → session cleared → re-navigating to /posts redirects to login
    private void validateSignOut(Browser browser) throws Exception {
        System.out.println("Testing: OAuth sign out");
        final BrowserContext context = browser.newContext();
        final Page page = context.newPage();

        // Login first
        page.navigate(BASE_URL + "/posts");
        waitFor(PAGE_INIT_MS);
        page.locator("button:has-text('Sign in')").click();
        page.waitForURL("**/posts**", new Page.WaitForURLOptions().setTimeout(10000));
        waitFor(PAGE_INIT_MS);

        // Click sign-out — triggers SetHref("/auth/signout")
        page.locator(".header-signout").click();
        waitFor(2000);

        // Now navigate to /posts — session removed, should redirect to login
        page.navigate(BASE_URL + "/posts");
        waitFor(PAGE_INIT_MS);

        assertTrue(page.url().contains("/auth/login"),
                "Should redirect to login after sign-out, but URL is: " + page.url());
        assertThat(page.locator("h1:has-text('Sign In')")).isVisible();

        context.close();
        System.out.println("✓ OAuth sign out validated");
    }

    // 4. Authorization denied → not authenticated
    private void validateAuthorizationDenied(Browser browser) throws Exception {
        System.out.println("Testing: OAuth authorization denied");
        final BrowserContext context = browser.newContext();
        final Page page = context.newPage();

        // Navigate to login page
        page.navigate(BASE_URL + "/posts");
        waitFor(PAGE_INIT_MS);
        assertTrue(page.url().contains("/auth/login"));

        // Configure stub to reject the next authorization request
        stubOAuth.rejectNext();

        // Click "Sign in" → StubOAuthServer returns error → callback has no code → redirect to /
        page.locator("button:has-text('Sign in')").click();
        waitFor(2000);

        // After the failed flow settles, navigate to /posts — should redirect to login (no session created)
        page.navigate(BASE_URL + "/posts");
        waitFor(PAGE_INIT_MS);

        assertTrue(page.url().contains("/auth/login"),
                "Should redirect to login after denied auth, but URL is: " + page.url());

        // Username should not be visible on login page
        assertEquals(0, page.locator(".header-username").count(),
                "Username should not be visible after denied auth");

        context.close();
        System.out.println("✓ Authorization denied validated");
    }

    private static Stream<BrowserType> browserTypes() {
        return Stream.of(playwright.chromium());
    }

    private static void waitFor(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }
}
