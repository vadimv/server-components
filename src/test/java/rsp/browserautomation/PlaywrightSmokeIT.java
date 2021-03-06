package rsp.browserautomation;

import com.microsoft.playwright.*;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@net.jcip.annotations.NotThreadSafe
public class PlaywrightSmokeIT {

    private static final int EXPECTED_RESPONSE_TIME_MS = 300;

    private static SimpleServer server;

    @BeforeClass
    public static void init() throws Exception {
        server = SimpleServer.run(false);
    }

    @Test
    public void should_pass_smoke_tests() throws Exception {
        final Playwright playwright = Playwright.create();
        final List<BrowserType> browserTypes = Arrays.asList(
                playwright.chromium(),
                playwright.webkit(),
                playwright.firefox()
        );

        for (final BrowserType browserType : browserTypes) {
            final Browser browser = browserType.launch();
            final BrowserContext context = browser.newContext();
            final Page page = context.newPage();

            validatePage(page);
        }
        playwright.close();
    }

    private void validatePage(Page page) throws InterruptedException {
        page.navigate("http://localhost:" + SimpleServer.PORT);
        Assert.assertEquals("test-server-title", page.title());

        assertElementTextEquals(page, "s0", "-1");
        assertElementStyleAttributeEquals(page, "s0",  "background-color","blue");
        waitForPageResponse();

        clickOnElement(page,"b0");
        waitForPageResponse();
        assertElementTextEquals(page,"s0", "0");
        assertElementStyleAttributeEquals(page, "s0", "background-color", "red");

        clickOnElement(page,"d0");
        waitForPageResponse();
        assertElementTextEquals(page,"s0", "10");
        assertElementStyleAttributeEquals(page, "s0","background-color", "red");
    }

    private void clickOnElement(Page page, String elementId) {
        page.click("#" + elementId);
    }

    private static void assertElementTextEquals(Page page, String elementId, String expectedValue) {
        Assert.assertEquals(expectedValue, page.innerText("#" + elementId));
    }

    private static void assertElementStyleAttributeEquals(Page page, String elementId, String styleName, String expectedValue) {
        final Optional<String> s = BrowserUtils.style(page.getAttribute("#" + elementId, "style"),
                                         styleName);
        s.ifPresentOrElse(v -> Assert.assertEquals(expectedValue, v), () -> Assert.fail());
    }

    private static void waitForPageResponse() throws InterruptedException {
        Thread.sleep(EXPECTED_RESPONSE_TIME_MS);
    }

    @AfterClass
    public static void shutdown() throws Exception {
        server.jetty.stop();
        Thread.sleep(2000);
    }
}
