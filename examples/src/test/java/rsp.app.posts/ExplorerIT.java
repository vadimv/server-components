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
 * End-to-end tests for the Explorer navigation functionality using Playwright.
 * Tests navigation between Posts and Comments views via the Explorer sidebar.
 */
@net.jcip.annotations.NotThreadSafe
    class ExplorerIT {

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
    void should_pass_explorer_navigation_tests(final BrowserType browserType) throws Exception {
        final Browser browser = browserType.launch();
        final BrowserContext context = browser.newContext();
        final Page page = context.newPage();
        System.out.println("Browser type: " + browserType.name());

        login(page);
        validateExplorerVisible(page);
        validateNavigationFromPostsToComments(page);
        validateNavigationFromCommentsToPosts(page);
        validateDirectUrlAccess(page);
        validateActiveStateIndicator(page);
    }

    private static Stream<BrowserType> browserTypes() {
        return Stream.of(playwright.chromium()
                   //      playwright.webkit(),
                   //      playwright.firefox()
                );
    }

    // ========== Test Scenarios ==========

    private void validateExplorerVisible(final Page page) throws InterruptedException {
        System.out.println("Testing: Explorer Sidebar Visibility");

        page.navigate(BASE_URL + "/posts");
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify explorer panel is visible
        Locator explorerPanel = page.locator(".explorer-panel");
        assertThat(explorerPanel).isVisible();

        // Verify explorer header
        Locator explorerHeader = page.locator(".explorer-header");
        assertThat(explorerHeader).isVisible();
        assertThat(explorerHeader).containsText("Explorer");

        // Verify explorer menu exists
        Locator explorerMenu = page.locator(".explorer-menu");
        assertThat(explorerMenu).isVisible();

        // Verify menu items exist (Posts and Comments)
        Locator menuItems = page.locator(".explorer-item");
        assertTrue(menuItems.count() >= 2, "Should have at least 2 menu items (Posts and Comments)");

        System.out.println("✓ Explorer sidebar validated successfully");
    }

    private void validateNavigationFromPostsToComments(final Page page) throws InterruptedException {
        System.out.println("Testing: Navigation from Posts to Comments");

        // Start on Posts page
        page.navigate(BASE_URL + "/posts");
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify we're on Posts
        assertTrue(page.url().contains("/posts"));
        assertThat(page.locator("h1")).containsText("Posts");

        // Click Comments link in explorer
        Locator commentsLink = page.locator(".explorer-item a:has-text(\"Comments\")");
        assertThat(commentsLink).isVisible();

        // Wait for URL to change after clicking (SPA navigation)
        String currentUrl = page.url();
        commentsLink.click();

        // Wait for URL to contain /comments (max 5 seconds)
        page.waitForURL(url -> url.contains("/comments"), new Page.WaitForURLOptions().setTimeout(5000));

        // Verify we're now on Comments
        assertTrue(page.url().contains("/comments"),
                  "Should navigate to /comments, but URL is: " + page.url());
        assertThat(page.locator("h1")).containsText("Comments");

        // Verify Comments table is visible
        assertThat(page.locator("table")).isVisible();

        System.out.println("✓ Navigation from Posts to Comments validated successfully");
    }

    private void validateNavigationFromCommentsToPosts(final Page page) throws InterruptedException {
        System.out.println("Testing: Navigation from Comments to Posts");

        // Start on Comments page
        page.navigate(BASE_URL + "/comments");
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify we're on Comments
        assertTrue(page.url().contains("/comments"));
        assertThat(page.locator("h1")).containsText("Comments");

        // Click Posts link in explorer
        Locator postsLink = page.locator(".explorer-item a:has-text(\"Posts\")");
        assertThat(postsLink).isVisible();

        // Wait for URL to change after clicking (SPA navigation)
        postsLink.click();

        // Wait for URL to contain /posts (max 5 seconds)
        page.waitForURL(url -> url.contains("/posts"), new Page.WaitForURLOptions().setTimeout(5000));

        // Verify we're now on Posts
        assertTrue(page.url().contains("/posts"),
                  "Should navigate to /posts, but URL is: " + page.url());
        assertThat(page.locator("h1")).containsText("Posts");

        // Verify Posts table is visible
        assertThat(page.locator("table")).isVisible();

        System.out.println("✓ Navigation from Comments to Posts validated successfully");
    }

    private void validateDirectUrlAccess(final Page page) throws InterruptedException {
        System.out.println("Testing: Direct URL Access");

        // Access Posts directly via URL
        page.navigate(BASE_URL + "/posts");
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        assertTrue(page.url().contains("/posts"));
        assertThat(page.locator("h1")).containsText("Posts");
        assertThat(page.locator(".explorer-panel")).isVisible();

        // Access Comments directly via URL
        page.navigate(BASE_URL + "/comments");
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        assertTrue(page.url().contains("/comments"));
        assertThat(page.locator("h1")).containsText("Comments");
        assertThat(page.locator(".explorer-panel")).isVisible();

        System.out.println("✓ Direct URL access validated successfully");
    }

    private void validateActiveStateIndicator(final Page page) throws InterruptedException {
        System.out.println("Testing: Active State Indicator in Explorer");

        // Navigate to Posts
        page.navigate(BASE_URL + "/posts");
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify Posts menu item is active.
        // The Explorer renders nodes recursively; group <li>s contain leaf <li>s in nested <ul>s.
        // ":has(> a:...)" with the direct-child combinator restricts the match to leaf items
        // (their <a> is a direct child) and excludes groups (their <a> is only transitively reachable).
        Locator postsMenuItem = page.locator(".explorer-item:has(> a:has-text(\"Posts\"))");
        String postsClass = postsMenuItem.getAttribute("class");
        assertTrue(postsClass != null && postsClass.contains("active"),
                  "Posts menu item should have 'active' class when on Posts page. Class: " + postsClass);

        // Navigate to Comments
        page.locator(".explorer-item > a:has-text(\"Comments\")").click();
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify Comments menu item is now active and Posts is not
        Locator commentsMenuItem = page.locator(".explorer-item:has(> a:has-text(\"Comments\"))");
        String commentsClass = commentsMenuItem.getAttribute("class");
        assertTrue(commentsClass != null && commentsClass.contains("active"),
                  "Comments menu item should have 'active' class when on Comments page. Class: " + commentsClass);

        // Verify Posts is no longer active
        postsClass = postsMenuItem.getAttribute("class");
        assertFalse(postsClass != null && postsClass.contains("active"),
                   "Posts menu item should NOT have 'active' class when on Comments page. Class: " + postsClass);

        System.out.println("✓ Active state indicator validated successfully");
    }

    // ========== Helper Methods ==========

    private void login(final Page page) throws InterruptedException {
        page.navigate(BASE_URL + "/posts");
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        page.locator("button:has-text('Sign in')").click();
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        page.waitForURL("**/posts**", new Page.WaitForURLOptions().setTimeout(5000));
    }

    private static void waitFor(final long timeMs) throws InterruptedException {
        Thread.sleep(timeMs);
    }
}
