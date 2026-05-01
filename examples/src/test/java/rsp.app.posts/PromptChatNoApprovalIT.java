package rsp.app.posts;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rsp.jetty.WebServer;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Verifies that chat-style prompts (TextReply) render without triggering the approval modal.
 * Reproduces the user's report: "I don't see the model's reply on the prompt panel".
 */
@net.jcip.annotations.NotThreadSafe
class PromptChatNoApprovalIT {

    private static final int PORT = 8086;
    private static final String BASE_URL = "http://localhost:" + PORT;

    private static final Playwright playwright = Playwright.create();
    private static WebServer server;

    @BeforeAll
    public static void init() {
        // Force regex backend so the test is deterministic and offline.
        System.setProperty("ai.agent", "regex");
        server = run();
    }

    private static WebServer run() {
        // Hand-rolled run on PORT 8086 to avoid clash with PromptSmokeIT (PORT 8085).
        return new CrudApp().run(false);
    }

    @AfterAll
    public static void shutdown() throws Exception {
        if (server != null) {
            server.stop();
        }
        Thread.sleep(2000);
    }

    @Test
    void chat_reply_appears_without_approval_modal() throws Exception {
        Browser browser = playwright.chromium().launch();
        BrowserContext context = browser.newContext();
        Page page = context.newPage();

        // Login
        page.navigate("http://localhost:8085/posts");
        Thread.sleep(300);
        page.locator("button:has-text('Sign in')").click();
        page.waitForURL("**/posts**", new Page.WaitForURLOptions().setTimeout(5000));

        Locator promptPanel = page.locator(".prompt-panel");
        assertThat(promptPanel).isVisible();

        Locator input = promptPanel.locator(".prompt-input");
        input.fill("hello");
        Thread.sleep(500);
        promptPanel.locator(".prompt-send").click();
        Thread.sleep(2000);
        // Print prompt panel state right after click for diagnosis
        System.err.println("[TEST] panel after click: " + promptPanel.innerHTML());

        // System should reply WITHOUT the approval modal appearing.
        // We expect at least one .prompt-message.system after a few seconds.
        page.waitForFunction(
                "() => document.querySelectorAll('.prompt-message.system').length >= 1",
                null,
                new Page.WaitForFunctionOptions().setTimeout(5000));

        // Modal should NOT be present.
        boolean modalPresent = page.locator(".btn-approve").count() > 0;
        if (modalPresent) {
            throw new AssertionError("Approval modal appeared for a chat-only prompt");
        }

        // Print what's actually in the panel for diagnosis.
        String html = promptPanel.innerHTML();
        System.out.println("===== PROMPT PANEL HTML =====");
        System.out.println(html);
        System.out.println("=============================");
    }
}
