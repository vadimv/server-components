package rsp.app.posts;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rsp.jetty.WebServer;

import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end smoke tests for the Prompt sidebar functionality.
 * Verifies echo responses across navigation (Posts -> Comments).
 * Tick messages are ignored in this test.
 */
@net.jcip.annotations.NotThreadSafe
class PromptSmokeIT {

    private static final int PORT = 8080;
    private static final int EXPECTED_PAGE_INIT_TIME_MS = 300;
    private static final String BASE_URL = "http://localhost:" + PORT;

    private static final Playwright playwright = Playwright.create();
    private static WebServer server;

    @BeforeAll
    public static void init() {
        server = CrudApp.run(false);
    }

    @AfterAll
    public static void shutdown() throws Exception {
        server.stop();
        Thread.sleep(2000);
    }

    @ParameterizedTest
    @MethodSource("browserTypes")
    void should_echo_messages_across_navigation(final BrowserType browserType) throws Exception {
        final Browser browser = browserType.launch();
        final BrowserContext context = browser.newContext();
        final Page page = context.newPage();
        System.out.println("Browser type: " + browserType.name());

        page.navigate(BASE_URL + "/posts");
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        assertThat(promptPanel(page)).isVisible();

        sendPromptAndExpectEcho(page, "message-1");

        navigateToComments(page);
        sendPromptAndExpectEcho(page, "message-2");
    }

    private static Stream<BrowserType> browserTypes() {
        return Stream.of(playwright.chromium()
                //      playwright.webkit(),
                //      playwright.firefox()
        );
    }

    private void sendPromptAndExpectEcho(final Page page, final String message) {
        Locator prompt = promptPanel(page);
        assertThat(prompt).isVisible();

        int echoCountBefore = echoMessages(page).count();

        Locator input = prompt.locator(".prompt-input");
        input.fill(message);
        input.press("Enter");

        waitForEchoCount(page, echoCountBefore + 1);
        assertTrue(echoMessages(page).count() >= echoCountBefore + 1,
                "Expected at least one new echo after sending: " + message);
    }

    private void navigateToComments(final Page page) {
        Locator commentsLink = page.locator(".explorer-item a:has-text(\"Comments\")");
        assertThat(commentsLink).isVisible();
        commentsLink.click();
        page.waitForURL(url -> url.contains("/comments"), new Page.WaitForURLOptions().setTimeout(5000));
    }

    private Locator promptPanel(final Page page) {
        return page.locator(".prompt-panel");
    }

    private Locator echoMessages(final Page page) {
        return page.locator(".prompt-message.system:has-text(\"echo-\")");
    }

    private void waitForEchoCount(final Page page, final int expectedMinCount) {
        page.waitForFunction(
                "expected => Array.from(document.querySelectorAll('.prompt-message.system'))" +
                        ".map(el => (el.textContent || '').trim())" +
                        ".filter(text => text.startsWith('echo-')).length >= expected",
                expectedMinCount,
                new Page.WaitForFunctionOptions().setTimeout(5000)
        );
    }

    private static void waitFor(final long timeMs) throws InterruptedException {
        Thread.sleep(timeMs);
    }
}
