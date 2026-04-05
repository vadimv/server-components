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
 * Verifies prompt submission and agent replies across navigation (Posts -> Comments).
 * The agent session requires approval; the test clicks through the approval dialog.
 */
@net.jcip.annotations.NotThreadSafe
class PromptSmokeIT {

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
    void should_echo_messages_across_navigation(final BrowserType browserType) throws Exception {
        final Browser browser = browserType.launch();
        final BrowserContext context = browser.newContext();
        final Page page = context.newPage();
        System.out.println("Browser type: " + browserType.name());

        login(page);

        assertThat(promptPanel(page)).isVisible();

        // First prompt triggers the approval dialog
        sendPrompt(page, "message-1");
        approveAgentDelegation(page);

        // After approval, the queued prompt is processed — wait for a system reply
        waitForSystemMessageCount(page, 1);

        // Navigate and send another prompt (agent session already approved)
        navigateToComments(page);
        sendPrompt(page, "message-2");
        int countBefore = systemMessages(page).count();
        waitForSystemMessageCount(page, countBefore + 1);

        assertTrue(systemMessages(page).count() >= 2,
                "Expected at least two system replies across navigation");
    }

    private static Stream<BrowserType> browserTypes() {
        return Stream.of(playwright.chromium()
                //      playwright.webkit(),
                //      playwright.firefox()
        );
    }

    private void sendPrompt(final Page page, final String message) {
        Locator prompt = promptPanel(page);
        assertThat(prompt).isVisible();

        Locator input = prompt.locator(".prompt-input");
        input.fill(message);
        input.press("Enter");
    }

    private void approveAgentDelegation(final Page page) {
        Locator approveButton = page.locator(".btn-approve");
        approveButton.waitFor(new Locator.WaitForOptions().setTimeout(5000));
        approveButton.click();
    }

    private void navigateToComments(final Page page) {
        Locator commentsLink = page.locator(".explorer-item a:has-text(\"Comments\")");
        assertThat(commentsLink).isVisible();
        commentsLink.click();
        page.waitForURL(url -> url.contains("/comments"), new Page.WaitForURLOptions().setTimeout(5000));
    }

    private void login(final Page page) throws InterruptedException {
        page.navigate(BASE_URL + "/posts");
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        page.locator("button:has-text('Sign in')").click();
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        page.waitForURL("**/posts**", new Page.WaitForURLOptions().setTimeout(5000));
    }

    private Locator promptPanel(final Page page) {
        return page.locator(".prompt-panel");
    }

    private Locator systemMessages(final Page page) {
        return page.locator(".prompt-message.system");
    }

    private void waitForSystemMessageCount(final Page page, final int expectedMinCount) {
        page.waitForFunction(
                "expected => document.querySelectorAll('.prompt-message.system').length >= expected",
                expectedMinCount,
                new Page.WaitForFunctionOptions().setTimeout(10000)
        );
    }

    private static void waitFor(final long timeMs) throws InterruptedException {
        Thread.sleep(timeMs);
    }
}
