package rsp.app.posts;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rsp.app.posts.services.RegexAgentService;
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
        // Use the regex agent so prompts like "show comments" produce NavigateResult,
        // which (per the new policy) triggers the approval modal.
        server = new CrudApp(new RegexAgentService()).run(false);
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

        // Action-bearing prompt (navigation) triggers the approval dialog
        sendPrompt(page, "show comments");
        approveAgentDelegation(page);

        // After approval, the spawn must succeed and the queued navigation must execute.
        page.waitForFunction(
                "() => Array.from(document.querySelectorAll('.prompt-message.system'))"
                        + ".some(el => el.textContent.includes('Agent access approved'))",
                null,
                new Page.WaitForFunctionOptions().setTimeout(10000));
        page.waitForURL(url -> url.contains("/comments"),
                new Page.WaitForURLOptions().setTimeout(5000));

        // Subsequent action prompt — already approved, should execute without modal.
        sendPrompt(page, "show posts");
        page.waitForURL(url -> url.contains("/posts"),
                new Page.WaitForURLOptions().setTimeout(5000));
        assertTrue(page.locator(".btn-approve").count() == 0,
                "Modal should not re-appear after approval");
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
